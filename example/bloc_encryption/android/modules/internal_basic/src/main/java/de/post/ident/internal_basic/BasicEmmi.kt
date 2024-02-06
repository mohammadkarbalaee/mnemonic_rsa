package de.post.ident.internal_basic

import de.post.ident.internal_core.rest.BranchDetailDTO
import de.post.ident.internal_core.rest.CoreEmmiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


suspend fun CoreEmmiService.getBranchesForLocation(latitude: Double, longitude: Double): List<BranchDetailDTO> = withContext(Dispatchers.IO) {
    restApi.get().path("branches")
            .queryParam("latitude", latitude.toString())
            .queryParam("longitude", longitude.toString())
            .executeList(BranchDetailDTO::class)
}
