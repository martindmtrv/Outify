package cc.tomko.outify.core

/**
 * Announces this device on the local network as a Spotify Connect target.
 *
 * Picking it in a Spotify client hands over that client's account, which
 * replaces the cached playback credentials and rebuilds the session.
 */
object Discovery {
    @JvmStatic
    external fun start(callback: DiscoveryCallback): Boolean

    @JvmStatic
    external fun stop()
}

interface DiscoveryCallback {
    /**
     * Called once the session runs with the account a Spotify client handed over
     */
    fun onLoggedIn(username: String)

    /**
     * Called when discovery stopped or the handed-over account could not be used
     */
    fun onFailed(reason: String)
}
