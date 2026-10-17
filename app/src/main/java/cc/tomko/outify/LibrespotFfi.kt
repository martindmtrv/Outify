package cc.tomko.outify

import android.content.Context

object LibrespotFfi {

    @JvmStatic
    external fun libInit(
        context: Context,
        clientId: String,
        clientSecret: String
    )

    @JvmStatic
    external fun updateClientCredentials(clientId: String, clientSecret: String)

    /**
     * Sets the icon shown in Spotify Connect device lists, e.g. "tv".
     * Applies to Connect runtimes built afterwards.
     */
    @JvmStatic
    external fun setDeviceType(deviceType: String)

    /**
     * Leaves volume to the system stream: the app outputs full scale and
     * Connect volume changes are applied to the device instead.
     * Applies to Connect runtimes built afterwards.
     */
    @JvmStatic
    external fun setHardwareVolume(enabled: Boolean)
}