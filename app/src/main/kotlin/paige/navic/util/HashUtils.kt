/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.util

import java.security.MessageDigest

fun md5Hex(input: String): String {
	val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
	return digest.joinToString("") { "%02x".format(it) }
}
