/*
 *     Highly configurable PaperDoll mod. Forked from Extra Player Renderer.
 *     Copyright (C) 2024-2025  LucunJi(Original author), HappyRespawnanchor
 *
 *     This file is part of Ayame PaperDoll.
 *
 *     Ayame PaperDoll is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Ayame PaperDoll is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with Ayame PaperDoll.  If not, see <https://www.gnu.org/licenses/>.
 */

architectury {
    common(rootProject.property("enabled_platforms").toString().split(','))
}

repositories {
    maven { url = uri("https://maven.fabricmc.net/") }
    mavenCentral()
}

dependencies {
    // Compile-time only. NeoForge provides these at runtime; this is the exact Mixin
    // and MixinExtras version on NeoForge's compile classpath.
    compileOnly("net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7")
    compileOnly("io.github.llamalad7:mixinextras-neoforge:0.5.4")
    //"minecraft"("com.mojang:minecraft:${rootProject.findProperty("minecraft_version")}")
}
loom {
    accessWidenerPath = file("src/main/resources/${rootProject.property("mod_access_widener_name")}")
}

// NeoForge-only fork: the common module has no playable client. Loom registers default
// client/server runs for every project; without a loader they try Fabric's KnotClient.
// Disable them so the only usable client task is :neoforge:runClient.
val currentProject = project
gradle.projectsEvaluated {
    val loomExtension = currentProject.extensions.getByType(net.fabricmc.loom.api.LoomGradleExtensionAPI::class.java)
    loomExtension.runConfigs.removeIf { it.name == "client" || it.name == "server" }
    listOf("runClient", "runServer").forEach { name ->
        currentProject.tasks.findByName(name)?.apply {
            enabled = false
            group = null
            description = "Unavailable in the common module - use :neoforge:runClient"
        }
    }
}

