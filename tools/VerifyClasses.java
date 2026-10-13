import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs bytecode verification of compiled plugin classes against a given server API jar.
 * <p>
 * Catches what a linkage scan can't: code compiled when a type was an enum (e.g. Sound on 1.14) where
 * javac omitted a checkcast, which fails verification once that type became an interface.
 * <p>
 * Usage: java -cp asm-jars VerifyClasses.java classesDir classpath(path-separated) [skipPrefix...]
 */
public class VerifyClasses
{
	public static void main(String[] args) throws IOException
	{
		Path classesDir = Path.of(args[0]);
		List<URL> urls = new ArrayList<>();
		urls.add(classesDir.toUri().toURL());
		for(String entry : args[1].split(File.pathSeparator))
			if(!entry.isEmpty())
				urls.add(new File(entry).toURI().toURL());
		List<String> skips = new ArrayList<>();
		for(int i = 2; i < args.length; i++)
			skips.add(args[i]);

		int failures = 0;
		try(URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
			Stream<Path> files = Files.walk(classesDir))
		{
			for(Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator)
			{
				String name = classesDir.relativize(file).toString().replace(File.separatorChar, '/');
				if(skips.stream().anyMatch(name::startsWith))
					continue;
				StringWriter out = new StringWriter();
				try
				{
					CheckClassAdapter.verify(new ClassReader(Files.readAllBytes(file)), loader, false, new PrintWriter(out));
				} catch(RuntimeException | LinkageError e)
				{
					out.append(String.valueOf(e));
				}
				if(out.getBuffer().length() > 0)
				{
					failures++;
					System.out.println("FAILED " + name + "\n" + out.toString().trim().lines().limit(6).reduce((a, b) -> a + "\n" + b).orElse(""));
				}
			}
		}
		System.out.println(failures + " class(es) failed verification");
		System.exit(failures == 0 ? 0 : 1);
	}
}
