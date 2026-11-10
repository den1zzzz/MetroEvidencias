using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

public static class WorkGroups {
    public static string Canonical(string text) {
        var decomposed=text.Normalize(NormalizationForm.FormD);
        var clean=new string(decomposed.Where(c=>CharUnicodeInfo.GetUnicodeCategory(c)!=UnicodeCategory.NonSpacingMark).ToArray());
        return Regex.Replace(clean.ToUpperInvariant(),@"[^\p{L}\p{Nd}]+"," ").Trim();
    }
    public static string Key(string site,string location,string shift,string date) {
        var place=string.IsNullOrWhiteSpace(site) ? location : site.StartsWith("OTHER:",StringComparison.OrdinalIgnoreCase) ? site+" "+location : site;
        var parts=new[]{Canonical(place),Canonical(shift),date};
        return Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(string.Join("|",parts))));
    }
    public static string Safe(string text) => Regex.Replace(text,@"[^\p{L}\p{Nd}_. -]","_").Trim().Trim('.').Trim();
}
