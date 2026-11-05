# Credits: music and texts on the radio computer

The radio computer's secret programs (1.12) ship some music and some scripture. Everything here is in the **public
domain**; nothing under a licence that asks for attribution or share-alike (CC BY, CC BY-SA) is used. Each source and its
licence were checked on its own page on 2026-11-05. Keep this list up to date when a file is added or replaced.

## Music (MUSIC.EXE)

MIDI files in `src/main/resources/assets/radiocraft/computer/music/`, listed (with titles and play order) in
`tracks.txt` there. All are from the [Mutopia Project](https://www.mutopiaproject.org/), whose pieces each state their
own licence; only pieces marked **Public Domain** were taken (see Mutopia's
[licensing page](https://www.mutopiaproject.org/legal.html#publicdomain)). The files are unchanged apart from their names.
They are played on the player's computer by Java's built-in software synthesizer (`javax.sound.midi`).

| File | Piece | Composer | Mutopia page | MIDI source | Licence |
|---|---|---|---|---|---|
| `entertainer.mid` | The Entertainer | Scott Joplin | [id 263](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=263) | https://www.mutopiaproject.org/ftp/JoplinS/entertainer/entertainer.mid | Public Domain |
| `maple_leaf_rag.mid` | Maple Leaf Rag | Scott Joplin | [id 23](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=23) | https://www.mutopiaproject.org/ftp/JoplinS/maple/maple.mid | Public Domain |
| `fur_elise.mid` | Für Elise, WoO 59 | Ludwig van Beethoven | [id 931](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=931) | https://www.mutopiaproject.org/ftp/BeethovenLv/WoO59/fur_Elise_WoO59/fur_Elise_WoO59.mid | Public Domain |
| `rondo_alla_turca.mid` | Sonata K. 331: Rondo alla Turca | W. A. Mozart | [id 108](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=108) | https://www.mutopiaproject.org/ftp/MozartWA/KV331/KV331_3_RondoAllaTurca/KV331_3_RondoAllaTurca.mid | Public Domain |
| `mountain_king.mid` | In the Hall of the Mountain King | Edvard Grieg | [id 1888](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1888) | https://www.mutopiaproject.org/ftp/GriegE/O46/Dans_l_antre_du_roi_de_la_montagne/Dans_l_antre_du_roi_de_la_montagne.mid | Public Domain |
| `arabesque_1.mid` | Première Arabesque | Claude Debussy | [id 1777](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1777) | https://www.mutopiaproject.org/ftp/DebussyC/L66/debussy_Arabesque_1/debussy_Arabesque_1.mid | Public Domain |
| `burgmuller_arabesque.mid` | L'Arabesque, Op. 100 No. 2 | J. F. F. Burgmüller | [id 203](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=203) | https://www.mutopiaproject.org/ftp/BurgmullerJFF/O100/25EF-02/25EF-02.mid | Public Domain |
| `greensleeves.mid` | Greensleeves (accordion) | Traditional | [id 1265](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1265) | https://www.mutopiaproject.org/ftp/Traditional/GreensleevesAcc/GreensleevesAcc.mid | Public Domain |
| `jesu_meine_freude.mid` | Jesu, meine Freude, BWV 610 | J. S. Bach | [id 706](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=706) | https://www.mutopiaproject.org/ftp/BachJS/BWV610/bwv610/bwv610.mid | Public Domain |
| `minuet_b_flat.mid` | Minuet in B-flat major, BWV Anh. 118 | J. S. Bach | [id 1014](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1014) | https://www.mutopiaproject.org/ftp/BachJS/BWVAnh118/BWV-118/BWV-118.mid | Public Domain |
| `belle_qui_tiens.mid` | Pavan: Belle qui tiens ma vie | Thoinot Arbeau | [id 1](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1) | https://www.mutopiaproject.org/ftp/ArbeauT/Orch/belle/belle.mid | Public Domain |
| `air.mid` | Air ("on the G string"), Suite No. 3, BWV 1068 | J. S. Bach | [id 1534](https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1534) | https://www.mutopiaproject.org/ftp/BachJS/BWV1068/bach_air_bmv_1068/bach_air_bmv_1068.mid | Public Domain |

Not taken: several of the same pieces in other Mutopia editions are CC BY-SA (for example Beethoven's Moonlight Sonata
id 276, Debussy's Deuxième Arabesque id 1994, the orchestral Air id 242).

## Scripture (BIBLE.EXE)

`src/main/resources/assets/radiocraft/computer/bible/web_gospels.txt.gz` holds the four gospels (Matthew, Mark, Luke,
John) of the **World English Bible**, 2020 stable text, from eBible.org's verse-per-line edition:
https://ebible.org/Scriptures/eng-web_vpl.zip (the XML file in it). eBible.org's notice in that archive: "The World
English Bible is in the Public Domain. That means that it is not copyrighted. However, 'World English Bible' is a
Trademark of eBible.org." They ask that a changed text not be called the World English Bible.

The text is not changed. Only its typography was made plain ASCII for the computer's text-mode font: curly quotes and
apostrophes became straight ones, the em dash `--`, the en dash `-`, and the ellipsis `...`. The script that makes the
file keeps no copy of the source: it reads the zip as data and writes the gzipped resource (one `#B <code> <name>` line
per book, then `<chapter>:<verse> <text>` per verse).
