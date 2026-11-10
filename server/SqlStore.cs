using Microsoft.Data.SqlClient;
using System.Data;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.RegularExpressions;

public sealed class SqlStore(string connectionString)
{
    public async Task<SqlConnection> Open()
    {
        var c = new SqlConnection(connectionString);
        try { await c.OpenAsync(); return c; } catch { await c.DisposeAsync(); throw; }
    }
    public static SqlCommand Command(SqlConnection c, string sql, params (string, object)[] values)
    {
        var cmd = new SqlCommand(sql, c);
        foreach (var (key, value) in values) {
            if(value is DBNull && key == "@original") cmd.Parameters.Add(key,SqlDbType.VarBinary,-1).Value=DBNull.Value;
            else if(value is DBNull && key == "@originalHash") cmd.Parameters.Add(key,SqlDbType.Binary,32).Value=DBNull.Value;
            else cmd.Parameters.AddWithValue(key,value);
        }
        return cmd;
    }
    public async Task Init()
    {
        var builder = new SqlConnectionStringBuilder(connectionString);
        var name = builder.InitialCatalog;
        if (!Regex.IsMatch(name, "^FotoEvidenciaEquans(?:Tests_[a-zA-Z0-9]+)?$"))
            throw new InvalidOperationException("Nombre de base no permitido para inicialización automática.");
        builder.InitialCatalog = "master";
        await using (var master = new SqlConnection(builder.ConnectionString))
        {
            await master.OpenAsync();
            using var exists = Command(master, "SELECT DB_ID(@name)", ("@name", name));
            if (await exists.ExecuteScalarAsync() is DBNull)
            {
                using var create = Command(master, $"CREATE DATABASE [{name}]");
                await create.ExecuteNonQueryAsync();
            }
        }
        await using var c = await Open();
        using var schema = Command(c, await File.ReadAllTextAsync(Path.Combine(AppContext.BaseDirectory, "schema.sql")));
        await schema.ExecuteNonQueryAsync();
    }
    public async Task<bool> HasUsers()
    {
        await using var c = await Open(); using var cmd = Command(c, "SELECT COUNT(*) FROM dbo.AppUsers");
        return (int)(await cmd.ExecuteScalarAsync())! > 0;
    }
    public async Task AddUser(string username, string password, string role)
    {
        if(username is null || password is null) throw new ArgumentException("Falta usuario o contraseña.");
        if (!Regex.IsMatch(username, "^[a-zA-Z0-9_.-]{3,50}$") || password.Length < 10 || password.Length > 200)
            throw new ArgumentException("Usuario: 3–50 letras, números, puntos o guiones. Contraseña: 10–200 caracteres.");
        var salt = RandomNumberGenerator.GetBytes(16);
        var hash = Rfc2898DeriveBytes.Pbkdf2(password, salt, 210000, HashAlgorithmName.SHA256, 32);
        await using var c = await Open();
        using var cmd = Command(c, "INSERT dbo.AppUsers(Username,PasswordHash,Salt,Role) VALUES(@u,@h,@s,@r)",
            ("@u", username), ("@h", hash), ("@s", salt), ("@r", role));
        try { await cmd.ExecuteNonQueryAsync(); }
        catch (SqlException e) when (e.Number is 2601 or 2627) { throw new ArgumentException("El usuario ya existe."); }
    }
    public async Task<object?> Login(string username, string password)
    {
        if(password.Length > 200) return null;
        await using var c = await Open();
        using var cmd = Command(c, "SELECT Username,PasswordHash,Salt,Role FROM dbo.AppUsers WHERE Username COLLATE Latin1_General_100_BIN2=@u", ("@u", username));
        string actual, role; byte[] expected, salt;
        await using(var reader = await cmd.ExecuteReaderAsync())
        {
            if(!await reader.ReadAsync()) {
                _ = Rfc2898DeriveBytes.Pbkdf2(password, new byte[16], 210000, HashAlgorithmName.SHA256, 32);
                return null;
            }
            actual = reader.GetString(0); expected = (byte[])reader[1]; salt = (byte[])reader[2]; role = reader.GetString(3);
        }
        var hash = Rfc2898DeriveBytes.Pbkdf2(password, salt, 210000, HashAlgorithmName.SHA256, 32);
        if(!CryptographicOperations.FixedTimeEquals(expected, hash)) return null;
        var token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
        using var insert = Command(c, "INSERT dbo.Sessions(TokenHash,Username,ExpiresAt) VALUES(@t,@u,@e); DELETE dbo.Sessions WHERE ExpiresAt<SYSDATETIMEOFFSET();",
            ("@t", TokenHash(token)), ("@u", actual), ("@e", DateTimeOffset.UtcNow.AddDays(7)));
        await insert.ExecuteNonQueryAsync();
        return new { token, role };
    }
    static byte[] TokenHash(string token) => SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(token));
    public async Task<Actor?> Session(string token)
    {
        if(token.Length != 64) return null;
        await using var c = await Open();
        using var cmd = Command(c, "SELECT u.Username,u.Role FROM dbo.Sessions s JOIN dbo.AppUsers u ON u.Username=s.Username WHERE s.TokenHash=@t AND s.ExpiresAt>SYSDATETIMEOFFSET()", ("@t", TokenHash(token)));
        await using var r = await cmd.ExecuteReaderAsync();
        return await r.ReadAsync() ? new Actor(r.GetString(0),r.GetString(1)) : null;
    }
    public async Task Logout(string token) {
        await using var c = await Open(); using var cmd = Command(c,"DELETE dbo.Sessions WHERE TokenHash=@t",("@t",TokenHash(token)));
        await cmd.ExecuteNonQueryAsync();
    }
    public async Task<bool> Upload(Upload input, string author)
    {
        if(input.Id is null || input.RecordId is null || input.Location is null || input.Tag is null || input.Description is null
            || input.Gps is null || input.Image is null) throw new ArgumentException("Datos incompletos.");
        if (!Guid.TryParse(input.Id,out var id) || !Guid.TryParse(input.RecordId,out var recordId) || input.Author != author
            || input.Type is not ("Mantenimiento" or "Avería") || string.IsNullOrWhiteSpace(input.Location)
            || input.Location.Length > 200 || string.IsNullOrWhiteSpace(input.Tag) || input.Tag.Length > 500
            || input.Description.Length > 2000 || input.Gps.Length > 500 || !DateTimeOffset.TryParse(input.CreatedAt,out var date))
            throw new ArgumentException("Datos de evidencia inválidos.");
        byte[] image;
        try { image = Convert.FromBase64String(input.Image); } catch(FormatException) { throw new ArgumentException("Imagen inválida."); }
        if(image.Length < 4 || image.Length > 18*1024*1024 || image[0]!=255 || image[1]!=216 || image[^2]!=255 || image[^1]!=217)
            throw new ArgumentException("Se requiere una fotografía JPEG de hasta 18 MB.");
        var shift = string.IsNullOrWhiteSpace(input.Shift) ? "Sin definir" : input.Shift;
        if(shift is not ("Diurno" or "Nocturno" or "Sin definir") || input.Environment is null || input.SiteCode is null || input.Activity is null
            || input.Environment.Length>120 || input.SiteCode.Length>150 || input.Activity.Length>80) throw new ArgumentException("Turno o ambiente inválido.");
        var workDate = string.IsNullOrWhiteSpace(input.WorkDate) ? DateOnly.FromDateTime(date.ToOffset(TimeSpan.FromHours(-5)).DateTime.AddDays(shift=="Nocturno" && date.ToOffset(TimeSpan.FromHours(-5)).Hour<8 ? -1 : 0)) :
            DateOnly.TryParseExact(input.WorkDate,"yyyy-MM-dd",out var parsedDate) ? parsedDate : throw new ArgumentException("Fecha de jornada inválida.");
        var environment = string.IsNullOrWhiteSpace(input.Environment) ? "General" : input.Environment.Trim();
        byte[]? original = null;
        if(!string.IsNullOrEmpty(input.OriginalImage)) {
            try { original=Convert.FromBase64String(input.OriginalImage); } catch(FormatException) { throw new ArgumentException("Original inválido."); }
            if(original.Length<4 || original.Length>18*1024*1024 || original[0]!=255 || original[1]!=216 || original[^2]!=255 || original[^1]!=217) throw new ArgumentException("Original JPEG inválido o superior a 18 MB.");
        }
        var originalHash = original is null ? null : SHA256.HashData(original);
        var digest = SHA256.HashData(image);
        var metadata = JsonSerializer.Serialize(new { id=id.ToString(), recordId=recordId.ToString(), author, type=input.Type,
            location=input.Location, tag=input.Tag, description=input.Description, gps=input.Gps, createdAt=date.ToUniversalTime().ToString("O") });
        if(input.Shift!="" || input.WorkDate!="" || input.Environment!="" || input.SiteCode!="" || input.Activity!="")
            metadata=JsonSerializer.Serialize(new { id=id.ToString(), recordId=recordId.ToString(), author, type=input.Type,
                location=input.Location, tag=input.Tag, description=input.Description, gps=input.Gps, createdAt=date.ToUniversalTime().ToString("O"),
                shift, workDate=workDate.ToString("yyyy-MM-dd"), environment, siteCode=input.SiteCode, activity=input.Activity });
        await using var c = await Open();
        using var tx = c.BeginTransaction(IsolationLevel.Serializable);
        using var find = Command(c, "SELECT ContentHash,Metadata,OriginalHash FROM dbo.Evidence WITH(UPDLOCK,HOLDLOCK) WHERE Id=@id", ("@id",id));
        find.Transaction = tx;
        await using(var reader = await find.ExecuteReaderAsync()) {
            if(await reader.ReadAsync()) {
                var same = CryptographicOperations.FixedTimeEquals((byte[])reader[0],digest) && reader.GetString(1)==metadata && (originalHash is null ? reader.IsDBNull(2) : !reader.IsDBNull(2) && CryptographicOperations.FixedTimeEquals((byte[])reader[2],originalHash));
                await reader.DisposeAsync(); tx.Commit(); return same;
            }
        }
        using var insert = Command(c, "INSERT dbo.Evidence(Id,RecordId,Author,WorkType,Location,Tag,Description,Gps,CapturedAt,Metadata,ContentHash,Image,Shift,WorkDate,Environment,SiteCode,Activity,OriginalImage,OriginalHash) VALUES(@id,@record,@author,@type,@place,@tag,@description,@gps,@date,@meta,@hash,@image,@shift,@workDate,@environment,@siteCode,@activity,@original,@originalHash)",
            ("@id",id),("@record",recordId),("@author",author),("@type",input.Type),("@place",input.Location),
            ("@tag",input.Tag),("@description",input.Description),("@gps",input.Gps),("@date",date),
            ("@meta",metadata),("@hash",digest),("@image",image),("@shift",shift),("@workDate",workDate.ToDateTime(TimeOnly.MinValue)),("@environment",environment),("@siteCode",input.SiteCode),("@activity",input.Activity),("@original",(object?)original ?? DBNull.Value),("@originalHash",(object?)originalHash ?? DBNull.Value));
        insert.Transaction = tx; await insert.ExecuteNonQueryAsync(); tx.Commit();
        return true;
    }
    public async Task<List<EvidenceItem>> Items()
    {
        await using var c = await Open();
        using var cmd = Command(c,"SELECT Id,RecordId,Author,WorkType,Location,Tag,Description,Gps,CapturedAt,Review,Reviewer,ReviewedAt,Shift,WorkDate,Environment,SiteCode,Activity,OriginalHash FROM dbo.Evidence ORDER BY CapturedAt DESC");
        await using var r = await cmd.ExecuteReaderAsync(); var list = new List<EvidenceItem>();
        while(await r.ReadAsync()) {
            var workDate=DateOnly.FromDateTime(r.GetDateTime(13)).ToString("yyyy-MM-dd");
            var key=WorkGroups.Key(r.GetString(15),r.GetString(4),r.GetString(12),workDate);
            list.Add(new EvidenceItem(r.GetGuid(0),r.GetGuid(1),r.GetString(2),r.GetString(3),r.GetString(4),r.GetString(5),r.GetString(6),r.GetString(7),r.GetDateTimeOffset(8),
                r.GetString(9),r.IsDBNull(10)?null:r.GetString(10),r.IsDBNull(11)?null:r.GetDateTimeOffset(11),r.GetString(12),workDate,r.GetString(14),r.GetString(15),r.GetString(16),key,!r.IsDBNull(17)));
        }
        return list;
    }
    public async Task<bool> Review(Guid id,string state,string reviewer)
    {
        if(state is not ("Revisada" or "Observada" or "Pendiente de revisión")) throw new ArgumentException("Estado inválido.");
        await using var c=await Open(); using var cmd=Command(c,"UPDATE dbo.Evidence SET Review=@s,Reviewer=@u,ReviewedAt=SYSDATETIMEOFFSET() WHERE Id=@id",("@s",state),("@u",reviewer),("@id",id));
        return await cmd.ExecuteNonQueryAsync()==1;
    }
    public async Task<byte[]?> Photo(Guid id, bool original = false) {
        await using var c=await Open(); using var cmd=Command(c,original ? "SELECT COALESCE(OriginalImage,Image) FROM dbo.Evidence WHERE Id=@id" : "SELECT Image FROM dbo.Evidence WHERE Id=@id",("@id",id));
        return await cmd.ExecuteScalarAsync() as byte[];
    }
}
public record Actor(string User,string Role);
public record Credentials(string User = "",string Password = "");
public record Upload(string Id="",string RecordId="",string Author="",string Type="",string Location="",string Tag="",string Description="",string Gps="",string CreatedAt="",string Image="",string Shift="",string WorkDate="",string Environment="",string SiteCode="",string Activity="",string? OriginalImage=null);
public record ReviewInput(Guid Id,string State);
public record PhotoInput(Guid Id,bool Original=false);
public record ExportInput(string GroupKey);
public record EvidenceItem(Guid Id,Guid RecordId,string Author,string Type,string Location,string Tag,string Description,string Gps,DateTimeOffset CreatedAt,string Review,string? Reviewer,DateTimeOffset? ReviewedAt,string Shift,string WorkDate,string Environment,string SiteCode,string Activity,string GroupKey,bool HasOriginal);




