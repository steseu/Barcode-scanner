using System.IO;
using System.Windows.Media.Imaging;
using QRCoder;

namespace BarcodeBridgeCompanion.Services;

public static class PairingQrCodeGenerator
{
    /// <summary>
    /// Matches the payload format read by the Android app's
    /// <c>PairingQrParser</c>: <c>barcodebridge://pair?host=&amp;port=&amp;token=</c>.
    /// </summary>
    public static string BuildPairingPayload(string host, int port, string token) =>
        $"barcodebridge://pair?host={host}&port={port}&token={token}";

    public static BitmapImage GeneratePng(string payload, int pixelsPerModule = 8)
    {
        using var qrGenerator = new QRCodeGenerator();
        using var qrData = qrGenerator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.M);
        using var qrCode = new PngByteQRCode(qrData);
        byte[] pngBytes = qrCode.GetGraphic(pixelsPerModule);

        var image = new BitmapImage();
        using (var stream = new MemoryStream(pngBytes))
        {
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.StreamSource = stream;
            image.EndInit();
        }
        image.Freeze();
        return image;
    }
}
