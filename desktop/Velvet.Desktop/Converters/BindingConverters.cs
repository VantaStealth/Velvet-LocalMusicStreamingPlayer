using System.Globalization;
using System.IO;
using System.Windows;
using System.Windows.Data;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace Velvet.Desktop.Converters;

public sealed class CoverImageConverter : IValueConverter
{
    private static readonly Dictionary<string, BitmapImage> Cache = new(StringComparer.OrdinalIgnoreCase);
    private static readonly object Gate = new();

    public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (value is not string path || path.Length == 0) return null;

        lock (Gate)
        {
            if (Cache.TryGetValue(path, out var cached)) return cached;
        }

        if (!File.Exists(path)) return null;

        try
        {
            var image = new BitmapImage();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.CreateOptions = BitmapCreateOptions.IgnoreImageCache;
            image.DecodePixelWidth = 640;
            image.UriSource = new Uri(path, UriKind.Absolute);
            image.EndInit();
            image.Freeze();

            lock (Gate)
            {
                Cache[path] = image;
            }
            return image;
        }
        catch
        {
            return null;
        }
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}

public sealed class ColorToBrushConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        var color = value switch
        {
            Color typed => typed,
            uint argb => Color.FromArgb(
                (byte)((argb >> 24) & 0xFF),
                (byte)((argb >> 16) & 0xFF),
                (byte)((argb >> 8) & 0xFF),
                (byte)(argb & 0xFF)),
            string text when uint.TryParse(text, out var parsed) => Color.FromArgb(
                (byte)((parsed >> 24) & 0xFF),
                (byte)((parsed >> 16) & 0xFF),
                (byte)((parsed >> 8) & 0xFF),
                (byte)(parsed & 0xFF)),
            _ => Colors.Transparent
        };

        var brush = new SolidColorBrush(color);
        brush.Freeze();
        return brush;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}

public sealed class InverseBooleanToVisibilityConverter : IValueConverter
{
    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture) =>
        value is true ? Visibility.Collapsed : Visibility.Visible;

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) =>
        value is Visibility.Collapsed;
}

public sealed class IdEqualsVisibilityConverter : IMultiValueConverter
{
    public object Convert(object[] values, Type targetType, object? parameter, CultureInfo culture)
    {
        if (values.Length < 2) return Visibility.Collapsed;

        var isEqual = string.Equals(
            values[0]?.ToString() ?? "",
            values[1]?.ToString() ?? "",
            StringComparison.Ordinal);

        var inverse = string.Equals(parameter as string, "inverse", StringComparison.OrdinalIgnoreCase);
        if (inverse) isEqual = !isEqual;
        return isEqual ? Visibility.Visible : Visibility.Collapsed;
    }

    public object[] ConvertBack(object value, Type[] targetTypes, object? parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}
