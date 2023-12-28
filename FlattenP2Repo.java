import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.file.PathUtils;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

public class FlattenP2Repo {

  public static void main(String[] args) throws IOException {
    if(args == null || args.length == 0) {
      throw new IOException("💥 No repository specified");
    }
    Path originalRepo = Path.of(args[0]);
    System.out.println("🛠 flattening " + originalRepo.toAbsolutePath());
    Path flatRepo = originalRepo.resolveSibling("flat-repository");
    if (Files.exists(flatRepo)) {
      PathUtils.deleteDirectory(flatRepo);
    }
    Files.createDirectory(flatRepo);

    var files = Files.walk(originalRepo).filter(path -> {
      if (!Files.isRegularFile(path)) {
        return false;
      }
      var fileName = FilenameUtils.getName(path.toString());
      return !fileName.startsWith("artifacts");
    }).collect(Collectors.toList());

    for (Path file : files) {
      PathUtils.copyFileToDirectory(file, flatRepo);
    }
    Path artifactsXml = extractAndRewriteArtifactXml(originalRepo.resolve("artifacts.jar"));
    createXZ(artifactsXml, flatRepo);
    createJar(artifactsXml, flatRepo);

    System.out.println("🙌 repository was flattened to " + flatRepo.toAbsolutePath());
  }

  private static Path extractAndRewriteArtifactXml(Path archive) throws IOException {
    var extracted = Files.createTempFile("artifacts", ".xml");
    try (JarInputStream archiveInputStream = new JarInputStream(
        new BufferedInputStream(Files.newInputStream(archive)))) {
      // we assume only 1 entry
      archiveInputStream.getNextJarEntry();
      streamRewrite(archiveInputStream, extracted);
    }
    if (Files.size(extracted) == 0) {
      throw new IOException("💥 Failed to extract/rewrite artifacts.xml");
    }
    return extracted;
  }

  private static void streamRewrite(InputStream src, Path dst) throws IOException {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(src));
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(dst)))) {
      String line;
      while ((line = br.readLine()) != null) {
        line = line.replace("/plugins/", "/").replace("/features/", "/");
        bw.write(line);
        bw.newLine();
      }
    }
  }

  private static void createXZ(Path artifactsXml, Path flatRepo) throws IOException {
    Path artifactsXmlXZ = flatRepo.resolve("artifacts.xml.xz");
    try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(artifactsXml));
        XZOutputStream xzOut = new XZOutputStream(
            new BufferedOutputStream(Files.newOutputStream(artifactsXmlXZ)), new LZMA2Options());) {
      byte[] buffer = new byte[4096];
      int n = 0;
      while (-1 != (n = in.read(buffer))) {
        xzOut.write(buffer, 0, n);
      }
    }
  }

  private static void createJar(Path artifactXml, Path flatRepo) throws IOException {
    Path artifactsJar = flatRepo.resolve("artifacts.jar").toAbsolutePath();
    var env = Collections.singletonMap("create", "true");// Create the zip file if it doesn't exist
    URI uri = URI.create("jar:" + artifactsJar.toUri());
    try (FileSystem zipfs = FileSystems.newFileSystem(uri, env)) {
      Path pathInZipfile = zipfs.getPath("artifacts.xml");
      Files.copy(artifactXml, pathInZipfile, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
