package zechs.drive.stream.utils

import zechs.drive.stream.domain.library.EpisodeParser as DomainEpisodeParser

typealias ParsedEpisode = DomainEpisodeParser.ParsedEpisode

/**
 * Fachada para compatibilidade retroativa em utils, delegando para o DomainEpisodeParser.
 */
object EpisodeParser {

    fun parse(filename: String): DomainEpisodeParser.ParsedEpisode = DomainEpisodeParser.parse(filename)

    fun cleanShowTitle(raw: String): String = DomainEpisodeParser.cleanShowTitle(raw)

    fun cleanEpisodeFileName(filename: String): String = DomainEpisodeParser.cleanEpisodeFileName(filename)

    fun extractCleanTechnicalTags(filename: String): String? = DomainEpisodeParser.extractCleanTechnicalTags(filename)

    fun naturalCompare(a: String, b: String): Int = DomainEpisodeParser.naturalCompare(a, b)
}
