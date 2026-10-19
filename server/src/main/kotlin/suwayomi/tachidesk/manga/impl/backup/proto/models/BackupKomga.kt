package suwayomi.tachidesk.manga.impl.backup.proto.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupKomgaConnection(
    @ProtoNumber(1) val apiUrl: String = "",
    @ProtoNumber(2) val browserUrl: String = "",
    @ProtoNumber(3) val identity: String = "",
    @ProtoNumber(4) val autoLink: Boolean = false,
    @ProtoNumber(5) val showCounter: Boolean = true,
)

@Serializable
data class BackupKomgaLink(
    @ProtoNumber(1) val seriesId: String = "",
    @ProtoNumber(2) val title: String = "",
    @ProtoNumber(3) val identity: String = "",
    @ProtoNumber(4) val provenance: String = "MANUAL",
)
