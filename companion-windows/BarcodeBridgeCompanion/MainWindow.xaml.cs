using System.Windows;
using BarcodeBridgeCompanion.Services;

namespace BarcodeBridgeCompanion;

public partial class MainWindow : Window
{
    private const int DefaultPort = 9999;

    private TcpScanServer? _server;

    public MainWindow()
    {
        InitializeComponent();
        StartServer(DefaultPort, GenerateToken());
    }

    private static string GenerateToken() => Guid.NewGuid().ToString("N")[..12];

    private void StartServer(int port, string token)
    {
        _server?.Dispose();

        var host = LocalNetworkInfo.GetLikelyLocalIPv4();
        var payload = PairingQrCodeGenerator.BuildPairingPayload(host, port, token);
        QrImage.Source = PairingQrCodeGenerator.GeneratePng(payload);
        ConnectionInfoText.Text = $"Host: {host}   Port: {port}   Token: {token}";

        _server = new TcpScanServer(port, token);
        _server.StatusChanged += OnStatusChanged;
        _server.ScanReceived += OnScanReceived;
        _server.Start();
    }

    private void OnStatusChanged(ServerStatus status)
    {
        Dispatcher.Invoke(() =>
        {
            StatusText.Text = status switch
            {
                ServerStatus.Listening => "Waiting for phone to connect...",
                ServerStatus.ClientConnected => "Connected - ready to receive scans",
                ServerStatus.Stopped => "Stopped",
                _ => StatusText.Text,
            };
        });
    }

    private void OnScanReceived(string text)
    {
        // Runs on the TCP server's background thread: type immediately so a
        // burst of scans in continuous-scan mode doesn't queue up behind the
        // UI thread, then hop to the UI thread only to update the log.
        bool pressEnter = Dispatcher.Invoke(() => SendEnterCheckBox.IsChecked == true);
        UnicodeKeyboardSender.Type(text, pressEnterAfter: pressEnter);
        Dispatcher.Invoke(() => ScanLogListBox.Items.Insert(0, $"{DateTime.Now:HH:mm:ss}  {text}"));
    }

    private void RegenerateTokenButton_Click(object sender, RoutedEventArgs e)
    {
        StartServer(_server?.Port ?? DefaultPort, GenerateToken());
    }

    protected override void OnClosed(EventArgs e)
    {
        _server?.Dispose();
        base.OnClosed(e);
    }
}
