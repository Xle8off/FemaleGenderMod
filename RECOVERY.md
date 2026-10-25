# Common source recovery and loader parity — beta.205 / Minecraft 26.2

The `common` project sources/resources were absent in this working copy. Before
restoring, the only remaining common source was MessageReceiptReveal.java.

Recovered the common bytecode from the retained, unprotected beta.202 compile
output (`neoforge/versions/26.2/build/classes/java/main`, excluding the platform
package). The immutable recovery input archive is retained locally at
`%TEMP%/springplus-neoforge-recovery/common-classes.zip`; the original beta.202 JAR
also remains in build/libs. Vineflower 1.12.0 recovered 198 top-level source files.
The original platform-specific NeoForge sources were preserved, not replaced with
upstream vanilla code. Generic inference and static initializer reconstruction
errors were repaired before compilation. Original common resources/AT were recovered
from the retained beta.202 build resources, not replaced by Fabric assets.

Recovered common build scaffolding from the upstream multiloader source snapshot
da61ca494de89cc89c9bb47fc783e059e2443dc1, then adapted to this project's existing
source sets. Original upstream author: WildfireRomeo and contributors; LGPL-3.0,
see root LICENSE and mod metadata. SpringPlus-specific logic remains from our
retained build or the current Fabric sources, not that upstream snapshot.

Latest loader-neutral GUI/music/camera sources are synchronized with the sibling
Fabric project. The camera transport now uses configured sender/capability callbacks;
Fabric and NeoForge wire their own networking APIs. Platform screen, frame submission,
disconnect cleanup and payload registration were adapted explicitly.

Native WebRTC Java API is Jar-in-Jar; native libraries and upstream native license
notices are bundled as resources, avoiding seven identical JPMS module names for
the classifier JARs. Native loading/conversion tests do not open devices.

Both published artifacts must be built and tested before a joint release. In
addition to per-loader checks, run SharedLoaderParityCheck against
`shared-ui-files.txt` to detect accidental divergence in the synchronized sources.
Do not label old loader artifacts with a new version. Recovery targets 26.2 only;
26.1 compatibility is not asserted. Real device, CF and in-game render QA remain
separate from compile/headless tests.
