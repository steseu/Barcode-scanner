using System.Net;
using System.Net.Sockets;
using System.Text;

namespace BarcodeBridgeCompanion.Services;

public enum ServerStatus
{
    Stopped,
    Listening,
    ClientConnected,
}

/// <summary>
/// Accepts the phone's TCP connection (transfer method B / "WLAN/TCP an
/// Companion-App"). The first line of every connection must be
/// <c>AUTH &lt;token&gt;</c> matching <see cref="Token"/> - this is the same
/// token shown in the pairing QR code, so a phone that never scanned it
/// (or scanned an old one) is rejected. Every following line is one scanned
/// value, delivered UTF-8 newline-terminated exactly as
/// <c>TcpTransport.kt</c> on the Android side writes it.
/// </summary>
public sealed class TcpScanServer : IDisposable
{
    public event Action<string>? ScanReceived;
    public event Action<ServerStatus>? StatusChanged;

    public int Port { get; }
    public string Token { get; }

    private readonly TcpListener _listener;
    private readonly CancellationTokenSource _cts = new();
    private Task? _acceptLoop;

    public TcpScanServer(int port, string token)
    {
        Port = port;
        Token = token;
        _listener = new TcpListener(IPAddress.Any, port);
    }

    public void Start()
    {
        _listener.Start();
        StatusChanged?.Invoke(ServerStatus.Listening);
        _acceptLoop = Task.Run(() => AcceptLoopAsync(_cts.Token));
    }

    private async Task AcceptLoopAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            TcpClient client;
            try
            {
                client = await _listener.AcceptTcpClientAsync(cancellationToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (ObjectDisposedException)
            {
                break;
            }

            _ = Task.Run(() => HandleClientAsync(client, cancellationToken));
        }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken cancellationToken)
    {
        using (client)
        using (var stream = client.GetStream())
        using (var reader = new StreamReader(stream, Encoding.UTF8))
        {
            var authLine = await reader.ReadLineAsync(cancellationToken);
            if (authLine is null || !IsAuthorized(authLine))
            {
                return; // silently drop unauthenticated/garbled connections
            }

            StatusChanged?.Invoke(ServerStatus.ClientConnected);
            try
            {
                string? line;
                while ((line = await reader.ReadLineAsync(cancellationToken)) is not null)
                {
                    if (line.Length > 0)
                    {
                        ScanReceived?.Invoke(line);
                    }
                }
            }
            finally
            {
                StatusChanged?.Invoke(ServerStatus.Listening);
            }
        }
    }

    private bool IsAuthorized(string authLine) =>
        authLine.StartsWith("AUTH ", StringComparison.Ordinal) &&
        authLine["AUTH ".Length..] == Token;

    public void Dispose()
    {
        _cts.Cancel();
        _listener.Stop();
        _cts.Dispose();
    }
}
