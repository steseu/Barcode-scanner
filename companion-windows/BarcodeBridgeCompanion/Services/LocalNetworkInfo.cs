using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace BarcodeBridgeCompanion.Services;

public static class LocalNetworkInfo
{
    /// <summary>Best-effort local IPv4 address to put in the pairing QR code (first active, non-loopback adapter).</summary>
    public static string GetLikelyLocalIPv4()
    {
        foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (nic.OperationalStatus != OperationalStatus.Up) continue;
            if (nic.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel) continue;

            foreach (var address in nic.GetIPProperties().UnicastAddresses)
            {
                if (address.Address.AddressFamily == AddressFamily.InterNetwork &&
                    !IPAddress.IsLoopback(address.Address))
                {
                    return address.Address.ToString();
                }
            }
        }
        return "127.0.0.1";
    }
}
