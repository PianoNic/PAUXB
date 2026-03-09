package ch.pianonic.pauxb.data

/**
 * Curated catalog of popular Linux GUI applications with pre-filled metadata.
 */
data class CatalogApp(
    val name: String,
    val packageName: String,
    val command: String,
    val description: String,
    val category: String
)

object AppCatalog {

    val categories = listOf(
        "Browsers",
        "Office",
        "Graphics",
        "Media",
        "Development",
        "System",
        "Games",
        "Communication"
    )

    val apps = listOf(
        // Browsers
        CatalogApp("Firefox", "firefox-esr", "firefox-esr", "Web browser by Mozilla", "Browsers"),
        CatalogApp("Chromium", "chromium", "chromium --no-sandbox", "Open-source Chrome browser", "Browsers"),
        CatalogApp("Midori", "midori", "midori", "Lightweight web browser", "Browsers"),

        // Office
        CatalogApp("LibreOffice Writer", "libreoffice-writer", "libreoffice --writer", "Word processor", "Office"),
        CatalogApp("LibreOffice Calc", "libreoffice-calc", "libreoffice --calc", "Spreadsheet editor", "Office"),
        CatalogApp("LibreOffice Impress", "libreoffice-impress", "libreoffice --impress", "Presentation editor", "Office"),
        CatalogApp("Evince", "evince", "evince", "PDF document viewer", "Office"),
        CatalogApp("Mousepad", "mousepad", "mousepad", "Simple text editor", "Office"),

        // Graphics
        CatalogApp("GIMP", "gimp", "gimp", "Image editor", "Graphics"),
        CatalogApp("Inkscape", "inkscape", "inkscape", "Vector graphics editor", "Graphics"),
        CatalogApp("Krita", "krita", "krita", "Digital painting app", "Graphics"),
        CatalogApp("Shotwell", "shotwell", "shotwell", "Photo manager", "Graphics"),
        CatalogApp("Eye of GNOME", "eog", "eog", "Image viewer", "Graphics"),

        // Media
        CatalogApp("VLC", "vlc", "vlc --no-xlib", "Media player", "Media"),
        CatalogApp("Audacity", "audacity", "audacity", "Audio editor", "Media"),
        CatalogApp("Rhythmbox", "rhythmbox", "rhythmbox", "Music player", "Media"),

        // Development
        CatalogApp("VS Code (OSS)", "codium", "codium --no-sandbox", "Code editor (open-source VS Code)", "Development"),
        CatalogApp("Geany", "geany", "geany", "Lightweight IDE", "Development"),
        CatalogApp("Bluefish", "bluefish", "bluefish", "Web development editor", "Development"),
        CatalogApp("Meld", "meld", "meld", "Visual diff and merge tool", "Development"),

        // System
        CatalogApp("Thunar", "thunar", "thunar", "File manager", "System"),
        CatalogApp("PCManFM", "pcmanfm", "pcmanfm", "File manager", "System"),
        CatalogApp("xterm", "xterm", "xterm", "Terminal emulator", "System"),
        CatalogApp("LXTerminal", "lxterminal", "lxterminal", "Terminal emulator", "System"),
        CatalogApp("LXDE Task Manager", "lxtask", "lxtask", "Task manager", "System"),
        CatalogApp("GParted", "gparted", "gparted", "Partition editor", "System"),
        CatalogApp("Synaptic", "synaptic", "synaptic", "Package manager GUI", "System"),

        // Games
        CatalogApp("Solitaire (AisleRiot)", "aisleriot", "sol", "Card games collection", "Games"),
        CatalogApp("GNOME Mines", "gnome-mines", "gnome-mines", "Minesweeper", "Games"),
        CatalogApp("GNOME Sudoku", "gnome-sudoku", "gnome-sudoku", "Sudoku puzzle", "Games"),
        CatalogApp("SuperTuxKart", "supertuxkart", "supertuxkart", "Racing game", "Games"),

        // Communication
        CatalogApp("HexChat", "hexchat", "hexchat", "IRC client", "Communication"),
        CatalogApp("Pidgin", "pidgin", "pidgin", "Multi-protocol messenger", "Communication"),
        CatalogApp("Thunderbird", "thunderbird", "thunderbird", "Email client", "Communication")
    )

    fun search(query: String): List<CatalogApp> {
        if (query.isBlank()) return apps
        val q = query.lowercase()
        return apps.filter {
            it.name.lowercase().contains(q) ||
            it.packageName.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.category.lowercase().contains(q)
        }
    }

    fun getByCategory(category: String): List<CatalogApp> {
        return apps.filter { it.category == category }
    }
}
