using System.Security.Cryptography;
using System.Threading.RateLimiting;
using Microsoft.AspNetCore.RateLimiting;

var builder=WebApplication.CreateBuilder(args);
builder.WebHost.ConfigureKestrel(o=>o.Limits.MaxRequestBodySize=52*1024*1024);
builder.Services.AddSingleton(new SqlStore(builder.Configuration.GetConnectionString("Equans") ?? throw new Exception("Falta conexión SQL")));
builder.Services.AddRateLimiter(o=>{
    o.RejectionStatusCode=429;
    o.AddPolicy("login",ctx=>RateLimitPartition.GetFixedWindowLimiter(ctx.Connection.RemoteIpAddress?.ToString()??"local",
        _=>new FixedWindowRateLimiterOptions { PermitLimit=20,Window=TimeSpan.FromMinutes(1),QueueLimit=0 }));
});
var app=builder.Build();
var store=app.Services.GetRequiredService<SqlStore>();
await store.Init();
if(!await store.HasUsers()) {
    var password=Environment.GetEnvironmentVariable("EQUANS_BOOTSTRAP_PASSWORD");
    if(string.IsNullOrEmpty(password)) {
        password=Convert.ToHexString(RandomNumberGenerator.GetBytes(15));
        var credentialFile=Path.Combine(app.Environment.ContentRootPath,"BootstrapCredentials.txt");
        await File.WriteAllTextAsync(credentialFile,$"Usuario: supervisor\nContraseña: {password}\nSolo para desarrollo local. No compartir este archivo.\n");
        Console.WriteLine($"Primer acceso guardado en: {credentialFile}");
    }
    await store.AddUser("supervisor",password,"supervisor");
}
app.Use(async(ctx,next)=>{
    ctx.Response.Headers.CacheControl="no-store";
    ctx.Response.Headers.XContentTypeOptions="nosniff";
    try { await next(); }
    catch(ArgumentException e) { if(!ctx.Response.HasStarted) { ctx.Response.StatusCode=400; await ctx.Response.WriteAsJsonAsync(new {error=e.Message}); } }
    catch(Exception e) {
        app.Logger.LogError(e,"Error en operación del servidor");
        if(!ctx.Response.HasStarted) {ctx.Response.StatusCode=500;await ctx.Response.WriteAsJsonAsync(new {error="No se completó la operación. Conserva los pendientes y reintenta."});}
    }
});
app.UseRateLimiter();
app.Use(async(ctx,next)=>{
    if(ctx.Request.Path is var path && (path=="/" || path=="/login" || path=="/health")) {await next();return;}
    var token=ctx.Request.Headers.Authorization.ToString().Replace("Bearer ","",StringComparison.Ordinal);
    var actor=await store.Session(token);
    if(actor is null) {ctx.Response.StatusCode=401;await ctx.Response.WriteAsJsonAsync(new {error="Inicia sesión otra vez"});return;}
    if(ctx.Request.Path!="/evidence" && ctx.Request.Path!="/logout" && actor.Role!="supervisor") {
        ctx.Response.StatusCode=403;await ctx.Response.WriteAsJsonAsync(new {error="Solo supervisor"});return;
    }
    ctx.Items["actor"]=actor; await next();
});
app.MapGet("/",()=>Results.File(Path.Combine(AppContext.BaseDirectory,"wwwroot","index.html"),"text/html; charset=utf-8"));
app.MapGet("/health",async()=>{await using var c=await store.Open();return Results.Ok(new {status="ok",storage="SQL Server"});});
app.MapPost("/login",async(Credentials input)=>{
    if(input.User is null || input.Password is null) return Results.BadRequest(new {error="Falta usuario o contraseña"});
    var result=await store.Login(input.User,input.Password);
    return result is null?Results.Json(new {error="Acceso incorrecto"},statusCode:401):Results.Ok(result);
}).RequireRateLimiting("login");
app.MapPost("/logout",async(HttpContext ctx)=>{await store.Logout(ctx.Request.Headers.Authorization.ToString().Replace("Bearer ",""));return Results.Ok(new {loggedOut=true});});
app.MapPost("/accounts",async(Credentials input)=>{await store.AddUser(input.User,input.Password,"worker");return Results.Ok(new {created=true});});
app.MapPost("/evidence",async(Upload input,HttpContext ctx)=>{
    var saved=await store.Upload(input,((Actor)ctx.Items["actor"]!).User);
    return saved?Results.Ok(new {id=input.Id,stored=true}):Results.Json(new {error="Identificador en conflicto"},statusCode:409);
});
app.MapPost("/items",async()=>Results.Ok(new {items=await store.Items()}));
app.MapPost("/review",async(ReviewInput input,HttpContext ctx)=>await store.Review(input.Id,input.State,((Actor)ctx.Items["actor"]!).User)?Results.Ok(new {saved=true}):Results.NotFound());
app.MapPost("/photo",async(PhotoInput input)=>{var image=await store.Photo(input.Id,input.Original);return image is null?Results.NotFound():Results.File(image,"image/jpeg");});
app.MapPost("/export",async(ExportInput input)=> {
    if(input.GroupKey is null || input.GroupKey.Length!=64) return Results.BadRequest();
    var photos=(await store.Items()).Where(x=>x.GroupKey==input.GroupKey).ToList();
    if(photos.Count==0)return Results.NotFound();
    var first=photos[0];
    var folder=string.Join("/",new[]{first.WorkDate,first.Location,first.Shift}.Select(WorkGroups.Safe));
    // Build on disk so a large shared shift does not allocate the entire ZIP in RAM.
    var temp=Path.Combine(Path.GetTempPath(),"equans-export-"+Guid.NewGuid()+".zip");
    try {
        await using(var target=File.Create(temp))
        using(var zip=new System.IO.Compression.ZipArchive(target,System.IO.Compression.ZipArchiveMode.Create,true)) {
            foreach(var photo in photos) {
                foreach(var original in photo.HasOriginal ? new[]{false,true} : new[]{false}) {
                    var bytes=await store.Photo(photo.Id,original); if(bytes is null)continue;
                    var name=folder+"/"+(original?"Originales/":"Etiquetadas/")+photo.Id+".jpg";
                    var entry=zip.CreateEntry(name,System.IO.Compression.CompressionLevel.NoCompression);
                    await using var output=entry.Open();await output.WriteAsync(bytes);
                }
            }
            var metadata=zip.CreateEntry(folder+"/datos.json");await using var stream=metadata.Open();
            await System.Text.Json.JsonSerializer.SerializeAsync(stream,photos,new System.Text.Json.JsonSerializerOptions(System.Text.Json.JsonSerializerDefaults.Web));
        }
        var file=new FileStream(temp,FileMode.Open,FileAccess.Read,FileShare.Read,65536,FileOptions.DeleteOnClose|FileOptions.Asynchronous);
        return Results.File(file,"application/zip",WorkGroups.Safe(first.Location+"-"+first.WorkDate+"-"+first.Shift)+".zip");
    } catch { if(File.Exists(temp)) File.Delete(temp);throw; }
});
await app.RunAsync();


