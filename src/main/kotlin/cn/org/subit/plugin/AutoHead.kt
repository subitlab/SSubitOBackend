@file:Suppress("PackageDirectoryMismatch")

package cn.org.subit.plugin.autohead

import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*

fun Application.installAutoHead() = install(AutoHeadResponse)