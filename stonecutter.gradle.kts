stonecutter active "26.3-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Version- and loader-specific properties from stonecutter.properties.toml
    properties {
        tags(version, loader)
    }

    // Lets source files use `//? if fabric {` etc.
    constants {
        match(loader, "fabric", "neoforge", "forge")
    }
}
