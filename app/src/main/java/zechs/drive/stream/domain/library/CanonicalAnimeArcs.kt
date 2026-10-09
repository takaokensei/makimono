package zechs.drive.stream.domain.library

/**
 * Definição canônica de arcos narrativos para séries e animes longos.
 */
data class CanonicalArc(
    val name: String,
    val startEp: Int,
    val endEp: Int
)

object CanonicalAnimeArcs {

    // One Piece Canonical Arcs (matching Netflix / Crunchyroll)
    val ONE_PIECE = listOf(
        CanonicalArc("Romance Dawn & East Blue", 1, 61),
        CanonicalArc("Alabasta", 62, 135),
        CanonicalArc("Skypiea", 136, 195),
        CanonicalArc("G-8 / The Naval Fortress", 196, 206),
        CanonicalArc("The Foxy Pirate Crew", 207, 228),
        CanonicalArc("Water Seven", 229, 263),
        CanonicalArc("Enies Lobby & CP9", 264, 312),
        CanonicalArc("Pós-Enies Lobby", 313, 325),
        CanonicalArc("Thriller Bark", 326, 384),
        CanonicalArc("Arquipélago de Sabaody", 385, 407),
        CanonicalArc("Amazon Lily & Impel Down", 408, 452),
        CanonicalArc("Guerra dos Maiorais / Marineford", 453, 516),
        CanonicalArc("Ilha dos Homens-Peixe", 517, 574),
        CanonicalArc("Punk Hazard", 575, 628),
        CanonicalArc("Dressrosa", 629, 746),
        CanonicalArc("Zou & Whole Cake Island", 747, 891),
        CanonicalArc("País de Wano", 892, 1085),
        CanonicalArc("Egghead Arc", 1086, 1200)
    )

    // Bleach Canonical Arcs
    val BLEACH = listOf(
        CanonicalArc("Shinigami Substituto", 1, 20),
        CanonicalArc("Sociedade das Almas", 21, 63),
        CanonicalArc("Bount", 64, 109),
        CanonicalArc("Arrancar: Chegada", 110, 131),
        CanonicalArc("Hueco Mundo", 132, 151),
        CanonicalArc("Arrancar: Luta Feroz", 152, 167),
        CanonicalArc("Capitão Amagai Shūsuke", 168, 189),
        CanonicalArc("Arrancar vs Shinigami", 190, 205),
        CanonicalArc("Passado", 206, 212),
        CanonicalArc("Batalha de Karakura", 213, 229),
        CanonicalArc("Zanpakutou Desconhecida", 230, 265),
        CanonicalArc("Queda dos Arrancars", 266, 316),
        CanonicalArc("Gotei 13 Invasão", 317, 342),
        CanonicalArc("Shinigami Substituto Desaparecido", 343, 366),
        CanonicalArc("Guerra Sangrenta dos Mil Anos (TYBW)", 367, 450)
    )

    // Naruto Shippuden Arcs
    val NARUTO_SHIPPUDEN = listOf(
        CanonicalArc("Resgate do Kazekage", 1, 32),
        CanonicalArc("Reencontro com Sasuke", 33, 53),
        CanonicalArc("Doze Guardiões Ninja", 54, 71),
        CanonicalArc("Hidan e Kakuzu", 72, 88),
        CanonicalArc("Três-Caudas", 89, 112),
        CanonicalArc("Perseguição a Itachi", 113, 143),
        CanonicalArc("Seis-Caudas", 144, 151),
        CanonicalArc("Dois Salvadores (Pain)", 152, 175),
        CanonicalArc("Passado: Lugar de Konoha", 176, 196),
        CanonicalArc("Reunião dos Cinco Kages", 197, 214),
        CanonicalArc("Quarta Guerra Mundial Ninja", 215, 375),
        CanonicalArc("Nascimento do Jinchuuriki do Dez-Caudas", 376, 479),
        CanonicalArc("Kaguya Otsutsuki", 480, 500)
    )

    // Hunter x Hunter Arcs
    val HUNTER_X_HUNTER = listOf(
        CanonicalArc("Exame Hunter", 1, 26),
        CanonicalArc("Família Zoldyck & Torre Celestial", 27, 58),
        CanonicalArc("Leilão de Yorknew (Trupe Fantasma)", 59, 75),
        CanonicalArc("Greed Island", 76, 92),
        CanonicalArc("Formigas Quimera (Chimera Ant)", 93, 136),
        CanonicalArc("Eleição do 13º Presidente", 137, 148)
    )
}
