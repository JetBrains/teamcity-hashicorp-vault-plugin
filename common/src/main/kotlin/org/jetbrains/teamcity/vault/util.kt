package org.jetbrains.teamcity.vault

import jetbrains.buildServer.util.VersionComparatorUtil


fun isJava8OrNewer(): Boolean {
    return VersionComparatorUtil.compare(System.getProperty("java.specification.version"), "1.8") >= 0
}