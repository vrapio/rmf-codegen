/**
 *  Copyright 2021 Michael van Tellingen
 */
package io.vrap.codegen.languages.go.client

import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.di.ClientPackageName
import io.vrap.rmf.codegen.di.SharedPackageName

class ClientConstants constructor (
    @SharedPackageName
    val sharedPackage: String,
    @ClientPackageName
    val clientPackage: String,
    @BasePackageName
    val basePackageName: String
) {

    val indexFile = "index"
}
