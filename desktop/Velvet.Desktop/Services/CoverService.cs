using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;

namespace Velvet.Desktop.Services;

public sealed class CoverService : IDisposable
{
    private const string CoverRevision = "r3";

    private readonly string _coversDir;
    private readonly object _gate = new();
    private readonly PrivateFontCollection _fonts = new();
    private readonly List<IntPtr> _fontMemory = new();
    private readonly FontFamily _serif;
    private readonly FontFamily _sans;
    private readonly Dictionary<string, (string Path, uint Color)> _generated = new(StringComparer.Ordinal);
    private bool _disposed;

    public CoverService(string coversDir)
    {
        _coversDir = coversDir;
        Directory.CreateDirectory(_coversDir);

        LoadEmbeddedFont("instrument_serif_regular.ttf");
        LoadEmbeddedFont("poppins_medium.ttf");

        _serif = _fonts.Families.FirstOrDefault(family => family.Name.Contains("Instrument", StringComparison.OrdinalIgnoreCase))
                 ?? new FontFamily("Georgia");
        _sans = _fonts.Families.FirstOrDefault(family => family.Name.Contains("Poppins", StringComparison.OrdinalIgnoreCase))
                ?? new FontFamily("Segoe UI");
    }

    public (string Path, uint Color)? SaveEmbeddedArtwork(byte[] bytes, string key)
    {
        try
        {
            var output = Path.Combine(_coversDir, $"art_{StableHash(key):x16}.jpg");
            using var source = new Bitmap(new MemoryStream(bytes));
            var color = DominantColor(source);

            if (!File.Exists(output) || new FileInfo(output).Length == 0)
            {
                lock (_gate)
                {
                    var scale = 640d / Math.Max(source.Width, source.Height);
                    var width = scale < 1 ? Math.Max(1, (int)(source.Width * scale)) : source.Width;
                    var height = scale < 1 ? Math.Max(1, (int)(source.Height * scale)) : source.Height;
                    using var target = new Bitmap(width, height, PixelFormat.Format24bppRgb);
                    using (var graphics = Graphics.FromImage(target))
                    {
                        graphics.CompositingQuality = CompositingQuality.HighQuality;
                        graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
                        graphics.SmoothingMode = SmoothingMode.HighQuality;
                        graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
                        graphics.DrawImage(source, 0, 0, width, height);
                    }
                    SaveJpeg(target, output, 88L);
                }
            }

            return (output, color);
        }
        catch
        {
            return null;
        }
    }

    public (string Path, uint Color) EnsureGeneratedCover(string seed, string monogram, uint? color = null)
    {
        lock (_gate)
        {
            if (_generated.TryGetValue(seed, out var cached)) return cached;

            var coverColor = color ?? PaletteColor(seed);
            var output = Path.Combine(_coversDir, $"cover_{CoverRevision}_{StableHash(seed):x16}.jpg");
            if (!File.Exists(output) || new FileInfo(output).Length == 0)
            {
                DrawCover(seed, coverColor, monogram, output);
            }

            var result = (output, coverColor);
            _generated[seed] = result;
            return result;
        }
    }

    public string GeneratedFileName(string seed) => $"cover_{CoverRevision}_{StableHash(seed):x16}.jpg";

    public static uint PaletteColor(string seed)
    {
        uint[] palettes =
        {
            0xFF8B5CF6, 0xFFA78BFA, 0xFF6D28D9,
            0xFFD4AF37, 0xFFE8C97A, 0xFFB8863B,
            0xFFE11D48, 0xFFFB7185, 0xFF9F1239,
            0xFF3B82F6, 0xFF60A5FA, 0xFF1D4ED8,
            0xFF0EA5E9, 0xFF14B8A6, 0xFF0F766E,
            0xFFF97316, 0xFFF59E0B, 0xFFC2410C,
            0xFF84CC16, 0xFF22C55E, 0xFF15803D,
            0xFFEC4899, 0xFFF472B6, 0xFFBE185D,
            0xFF6366F1, 0xFF818CF8, 0xFF4338CA
        };
        return palettes[(int)(StableHash(seed) % (ulong)palettes.Length)];
    }

    public static string Monogram(string source)
    {
        var cleaned = Regex.Replace(source, @"\(.*?\)|\[.*?\]", " ").Trim();
        var ignored = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "the", "a", "an", "and", "of", "for", "to", "in", "on",
            "feat", "ft", "with", "unknown", "album", "untitled"
        };
        var words = Regex.Split(cleaned, @"[\s._\-/]+")
            .Where(word => word.Length > 0 && !ignored.Contains(word))
            .ToList();
        var initials = words
            .Select(word => word.FirstOrDefault(char.IsLetterOrDigit))
            .Where(letter => letter != '\0')
            .Take(2)
            .ToArray();
        return initials.Length switch
        {
            >= 2 => new string(initials).ToUpperInvariant(),
            1 => new string(cleaned.Where(char.IsLetterOrDigit).Take(2).ToArray()).ToUpperInvariant().PadRight(1, 'V'),
            _ => "V"
        };
    }

    public static ulong StableHash(string value)
    {
        var hash = 14695981039346656037UL;
        foreach (var character in value)
        {
            hash ^= character;
            hash *= 1099511628211UL;
        }
        return hash;
    }

    private void DrawCover(string seed, uint colorArgb, string monogram, string output)
    {
        const int size = 560;
        var accent = ArgbToColor(colorArgb);
        var mid = Scale(accent, 0.62f);
        var deep = Scale(accent, 0.32f);
        var ink = Color.FromArgb(7, 6, 10);
        var cream = Color.FromArgb(247, 240, 227);
        var gold = Color.FromArgb(232, 201, 122);

        using var bitmap = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        bitmap.SetResolution(96, 96);
        using var graphics = Graphics.FromImage(bitmap);
        graphics.SmoothingMode = SmoothingMode.AntiAlias;
        graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
        graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
        graphics.CompositingQuality = CompositingQuality.HighQuality;
        graphics.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;

        var bounds = new Rectangle(0, 0, size, size);
        using (var background = new LinearGradientBrush(bounds, mid, ink, 45f))
        {
            background.InterpolationColors = new ColorBlend
            {
                Colors = new[] { mid, deep, ink },
                Positions = new[] { 0f, 0.55f, 1f }
            };
            graphics.FillRectangle(background, bounds);
        }

        DrawGlow(graphics, size * 0.78f, size * 0.20f, size * 0.64f, Color.FromArgb(150, accent));
        DrawGlow(graphics, size * 0.06f, size * 0.96f, size * 0.50f, Color.FromArgb(48, gold));

        var discX = size * 0.755f;
        var discY = size * 0.45f;
        var discR = size * 0.24f;
        var discBounds = new RectangleF(discX - discR, discY - discR, discR * 2, discR * 2);
        using (var disc = new LinearGradientBrush(
                   new PointF(discX - discR, discY - discR),
                   new PointF(discX + discR, discY + discR),
                   Color.FromArgb(31, 26, 34),
                   Color.FromArgb(4, 3, 6)))
        {
            disc.InterpolationColors = new ColorBlend
            {
                Colors = new[] { Color.FromArgb(35, 30, 38), Color.FromArgb(11, 9, 14), Color.FromArgb(4, 3, 6) },
                Positions = new[] { 0f, 0.62f, 1f }
            };
            graphics.FillEllipse(disc, discBounds);
        }

        for (var ring = 0; ring < 22; ring++)
        {
            using var groove = new Pen(Color.FromArgb(8 + ring % 4 * 4, 255, 255, 255), 1.1f);
            var radius = discR * (0.14f + ring * 0.038f);
            graphics.DrawEllipse(groove, discX - radius, discY - radius, radius * 2, radius * 2);
        }

        using (var gloss = new LinearGradientBrush(
                   new PointF(discX - discR, discY - discR),
                   new PointF(discX + discR * 0.45f, discY + discR),
                   Color.FromArgb(42, 255, 255, 255),
                   Color.FromArgb(0, 255, 255, 255)))
        {
            graphics.FillEllipse(gloss, discBounds);
        }

        using (var rim = new Pen(Color.FromArgb(165, 250, 237, 199), 2f))
        {
            graphics.DrawEllipse(rim, discBounds);
            graphics.DrawArc(rim, discBounds, 196f, 118f);
        }

        var labelR = discR * 0.34f;
        using (var label = new SolidBrush(accent))
        {
            graphics.FillEllipse(label, discX - labelR, discY - labelR, labelR * 2, labelR * 2);
        }
        using (var labelRing = new Pen(Color.FromArgb(115, 250, 237, 199), 2.4f))
        {
            graphics.DrawEllipse(labelRing, discX - labelR, discY - labelR, labelR * 2, labelR * 2);
        }
        using (var hole = new SolidBrush(Color.FromArgb(230, 7, 6, 10)))
        {
            var holeR = discR * 0.035f;
            graphics.FillEllipse(hole, discX - holeR, discY - holeR, holeR * 2, holeR * 2);
        }

        using var typeface = new Font(_serif, size * 0.30f, FontStyle.Regular, GraphicsUnit.Pixel);
        using var typographic = StringFormat.GenericTypographic;
        var textX = size * 0.075f;
        var textY = size * 0.315f;
        using (var shadowBrush = new SolidBrush(Color.FromArgb(110, 0, 0, 0)))
        {
            graphics.DrawString(monogram, typeface, shadowBrush, textX + 2.5f, textY + 3f, typographic);
        }
        using (var creamBrush = new SolidBrush(Color.FromArgb(240, cream)))
        {
            graphics.DrawString(monogram, typeface, creamBrush, textX, textY, typographic);
        }

        using (var rule = new Pen(Color.FromArgb(175, gold), 1.7f))
        {
            graphics.DrawLine(rule, textX, size * 0.605f, textX + size * 0.17f, size * 0.605f);
        }

        using (var captionFont = new Font(_sans, size * 0.026f, FontStyle.Regular, GraphicsUnit.Pixel))
        using (var captionBrush = new SolidBrush(Color.FromArgb(190, gold)))
        {
            DrawTrackedText(graphics, "VELVET PRESSING", textX, size * 0.646f, captionFont, captionBrush, size * 0.0072f);
        }

        using (var starBrush = new SolidBrush(Color.FromArgb(215, cream)))
        {
            var starX = size * 0.90f;
            var starY = size * 0.11f;
            var starR = size * 0.018f;
            var star = new[]
            {
                new PointF(starX, starY - starR * 2.2f),
                new PointF(starX + starR * 0.6f, starY - starR * 0.6f),
                new PointF(starX + starR * 2.2f, starY),
                new PointF(starX + starR * 0.6f, starY + starR * 0.6f),
                new PointF(starX, starY + starR * 2.2f),
                new PointF(starX - starR * 0.6f, starY + starR * 0.6f),
                new PointF(starX - starR * 2.2f, starY),
                new PointF(starX - starR * 0.6f, starY - starR * 0.6f)
            };
            graphics.FillPolygon(starBrush, star);
        }

        var random = new Random(unchecked((int)StableHash(seed)));
        for (var index = 0; index < 760; index++)
        {
            using var grain = new SolidBrush(random.Next(2) == 0
                ? Color.FromArgb(6, 255, 255, 255)
                : Color.FromArgb(7, 0, 0, 0));
            var x = random.Next(size);
            var y = random.Next(size);
            graphics.FillRectangle(grain, x, y, 1.3f, 1.3f);
        }

        using (var vignettePath = new GraphicsPath())
        {
            vignettePath.AddRectangle(new RectangleF(-size * 0.2f, -size * 0.2f, size * 1.4f, size * 1.4f));
            using var vignette = new PathGradientBrush(vignettePath)
            {
                CenterPoint = new PointF(size * 0.5f, size * 0.47f),
                CenterColor = Color.FromArgb(0, 0, 0, 0),
                SurroundColors = new[] { Color.FromArgb(170, 0, 0, 0) }
            };
            vignette.FocusScales = new PointF(0.55f, 0.55f);
            graphics.FillRectangle(vignette, bounds);
        }

        SaveJpeg(bitmap, output, 88L);
    }

    private void DrawGlow(Graphics graphics, float x, float y, float radius, Color color)
    {
        using var path = new GraphicsPath();
        path.AddEllipse(x - radius, y - radius, radius * 2, radius * 2);
        using var brush = new PathGradientBrush(path)
        {
            CenterPoint = new PointF(x, y),
            CenterColor = color,
            SurroundColors = new[] { Color.FromArgb(0, color) }
        };
        graphics.FillEllipse(brush, x - radius, y - radius, radius * 2, radius * 2);
    }

    private static void DrawTrackedText(
        Graphics graphics, string text, float x, float y, Font font, Brush brush, float tracking)
    {
        foreach (var character in text)
        {
            graphics.DrawString(character.ToString(), font, brush, x, y);
            x += graphics.MeasureString(character.ToString(), font, PointF.Empty, StringFormat.GenericTypographic).Width + tracking;
        }
    }

    private static uint DominantColor(Bitmap source)
    {
        using var small = new Bitmap(source, new Size(24, 24));
        double red = 0, green = 0, blue = 0, weightSum = 0;
        double fallbackRed = 0, fallbackGreen = 0, fallbackBlue = 0;

        for (var y = 0; y < small.Height; y++)
        {
            for (var x = 0; x < small.Width; x++)
            {
                var pixel = small.GetPixel(x, y);
                fallbackRed += pixel.R;
                fallbackGreen += pixel.G;
                fallbackBlue += pixel.B;

                var max = Math.Max(pixel.R, Math.Max(pixel.G, pixel.B)) / 255.0;
                var min = Math.Min(pixel.R, Math.Min(pixel.G, pixel.B)) / 255.0;
                var saturation = max - min;
                var luminance = (0.299 * pixel.R + 0.587 * pixel.G + 0.114 * pixel.B) / 255.0;
                if (luminance < 0.06 || luminance > 0.94) continue;

                var weight = 0.2 + saturation * saturation * 2.2;
                red += pixel.R * weight;
                green += pixel.G * weight;
                blue += pixel.B * weight;
                weightSum += weight;
            }
        }

        var count = small.Width * small.Height;
        Color baseColor;
        if (weightSum > 0)
        {
            baseColor = Color.FromArgb(
                ClampByte(red / weightSum), ClampByte(green / weightSum), ClampByte(blue / weightSum));
        }
        else
        {
            baseColor = Color.FromArgb(
                ClampByte(fallbackRed / count), ClampByte(fallbackGreen / count), ClampByte(fallbackBlue / count));
        }

        var brightness = baseColor.GetBrightness();
        var adjusted = FromHsv(
            baseColor.GetHue(),
            Math.Max(baseColor.GetSaturation(), 0.42),
            Math.Clamp(brightness, 0.62, 0.92));
        return Argb(adjusted);
    }

    private static Color FromHsv(double hue, double saturation, double value)
    {
        var chroma = value * saturation;
        var section = hue / 60.0;
        var x = chroma * (1 - Math.Abs(section % 2 - 1));
        (double r, double g, double b) = section switch
        {
            < 1 => (chroma, x, 0d),
            < 2 => (x, chroma, 0d),
            < 3 => (0d, chroma, x),
            < 4 => (0d, x, chroma),
            < 5 => (x, 0d, chroma),
            _ => (chroma, 0d, x)
        };
        var m = value - chroma;
        return Color.FromArgb(
            ClampByte((r + m) * 255), ClampByte((g + m) * 255), ClampByte((b + m) * 255));
    }

    private static void SaveJpeg(Bitmap bitmap, string path, long quality)
    {
        var encoder = ImageCodecInfo.GetImageEncoders().First(codec => codec.FormatID == ImageFormat.Jpeg.Guid);
        using var parameters = new EncoderParameters(1);
        parameters.Param[0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, quality);
        var temp = path + ".tmp";
        bitmap.Save(temp, encoder, parameters);
        File.Move(temp, path, true);
    }

    private static Color Scale(Color color, float factor) => Color.FromArgb(
        ClampByte(color.R * factor), ClampByte(color.G * factor), ClampByte(color.B * factor));

    private static Color ArgbToColor(uint argb) => Color.FromArgb(
        (int)((argb >> 24) & 0xFF), (int)((argb >> 16) & 0xFF), (int)((argb >> 8) & 0xFF), (int)(argb & 0xFF));

    private static uint Argb(Color color) =>
        0xFF000000 | ((uint)color.R << 16) | ((uint)color.G << 8) | color.B;

    private static int ClampByte(double value) => Math.Clamp((int)Math.Round(value), 0, 255);

    private void LoadEmbeddedFont(string fileName)
    {
        try
        {
            var resource = System.Windows.Application.GetResourceStream(
                new Uri($"pack://application:,,,/Assets/Fonts/{fileName}", UriKind.Absolute));
            if (resource is null) return;

            using var stream = resource.Stream;
            var bytes = new byte[stream.Length];
            stream.ReadExactly(bytes);
            var pointer = Marshal.AllocHGlobal(bytes.Length);
            Marshal.Copy(bytes, 0, pointer, bytes.Length);
            _fontMemory.Add(pointer);
            _fonts.AddMemoryFont(pointer, bytes.Length);
        }
        catch
        {
            // Fall back to a system serif or sans face.
        }
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        _fonts.Dispose();
        foreach (var pointer in _fontMemory) Marshal.FreeHGlobal(pointer);
        _fontMemory.Clear();
        GC.SuppressFinalize(this);
    }
}
