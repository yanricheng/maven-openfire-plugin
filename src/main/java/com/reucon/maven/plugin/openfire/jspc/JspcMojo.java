//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.

package com.reucon.maven.plugin.openfire.jspc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.jasper.JspC;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.PatternMatcher;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.resource.Resource;

/**
 * <p>
 * This goal will compile jsps for a webapp so that they can be included in a
 * war.
 * </p>
 * <p>
 * At runtime, the plugin will use the jsp2.0 jspc compiler if you are running
 * on a 1.4 or lower jvm. If you are using a 1.5 jvm, then the jsp2.1 compiler
 * will be selected. (this is the same behaviour as the <a
 * href="http://jetty.mortbay.org/maven-plugin">jetty plugin</a> for executing
 * webapps).
 * </p>
 * <p>
 * Note that the same java compiler will be used as for on-the-fly compiled
 * jsps, which will be the Eclipse java compiler.
 * </p>
 * 
 * <p>
 * See <a
 * href="http://docs.codehaus.org/display/JETTY/Maven+Jetty+Jspc+Plugin">Usage
 * Guide</a> for instructions on using this plugin.
 * </p>
 * 
 * @author janb
 * 
 * @goal jspc
 * @phase process-classes
 * @requiresDependencyResolution compile
 * @description Runs jspc compiler to produce .java and .class files
 */
public class JspcMojo extends AbstractMojo
{
    public static final String END_OF_WEBAPP = "</web-app>";


    /**
     * Whether or not to include dependencies on the plugin's classpath with &lt;scope&gt;provided&lt;/scope&gt;
     * Use WITH CAUTION as you may wind up with duplicate jars/classes.
     * 
     * @since jetty-7.6.3
     * @parameter  default-value="false"
     */
    private boolean useProvidedScope;
    
    /**
     * The artifacts for the project.
     * 
     * @since jetty-7.6.3
     * @parameter expression="${project.artifacts}"
     * @readonly
     */
    private Set projectArtifacts;
    
    
    /**
     * The maven project.
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    

    /**
     * The artifacts for the plugin itself.
     * 
     * @parameter expression="${plugin.artifacts}"
     * @readonly
     */
    private List pluginArtifacts;
    
    
    /**
     * File into which to generate the &lt;servlet&gt; and
     * &lt;servlet-mapping&gt; tags for the compiled jsps
     * 
     * @parameter default-value="${basedir}/target/webfrag.xml"
     */
    private String webXmlFragment;

    /**
     * Optional. A marker string in the src web.xml file which indicates where
     * to merge in the generated web.xml fragment. Note that the marker string
     * will NOT be preserved during the insertion. Can be left blank, in which
     * case the generated fragment is inserted just before the &lt;/web-app&gt;
     * line
     * 
     * @parameter
     */
    private String insertionMarker;

    /**
     * Merge the generated fragment file with the web.xml from
     * webAppSourceDirectory. The merged file will go into the same directory as
     * the webXmlFragment.
     * 
     * @parameter default-value="true"
     */
    private boolean mergeFragment;

    /**
     * The destination directory into which to put the compiled jsps.
     * 
     * @parameter default-value="${project.build.outputDirectory}"
     */
    private String generatedClasses;

    /**
     * Controls whether or not .java files generated during compilation will be
     * preserved.
     * 
     * @parameter default-value="false"
     */
    private boolean keepSources;

    /**
     * Default root package for all generated classes
     * 
     * @parameter default-value="jsp"
     */
    private String packageRoot;

    /**
     * Root directory for all html/jsp etc files
     * 
     * @parameter default-value="${basedir}/src/main/webapp"
     * 
     */
    private String webAppSourceDirectory;
    
   
    
    /**
     * Location of web.xml. Defaults to src/main/webapp/web.xml.
     * @parameter default-value="${basedir}/src/main/webapp/WEB-INF/web.xml"
     */
    private String webXml;


    /**
     * The comma separated list of patterns for file extensions to be processed. By default
     * will include all .jsp and .jspx files.
     * 
     * @parameter default-value="**\/*.jsp, **\/*.jspx"
     */
    private String includes;

    /**
     * The comma separated list of file name patters to exclude from compilation.
     * 
     * @parameter default_value="**\/.svn\/**";
     */
    private String excludes;

    /**
     * The location of the compiled classes for the webapp
     * 
     * @parameter expression="${project.build.outputDirectory}"
     */
    private File classesDirectory;
    
    /**
     * The location of the compiled classes for the webapp
     * 
     * @parameter expression="${project.build.outputDirectory}/jspcj"
     */
    private String japcJavaDirectory;

    /**
     * Whether or not to output more verbose messages during compilation.
     * 
     * @parameter default-value="false";
     */
    private boolean verbose;

    /**
     * If true, validates tlds when parsing.
     * 
     * @parameter default-value="false";
     */
    private boolean validateXml;

    /**
     * The encoding scheme to use.
     * 
     * @parameter default-value="UTF-8"
     */
    private String javaEncoding;

    /**
     * Whether or not to generate JSR45 compliant debug info
     * 
     * @parameter default-value="true";
     */
    private boolean suppressSmap;

    /**
     * Whether or not to ignore precompilation errors caused by jsp fragments.
     * 
     * @parameter default-value="false"
     */
    private boolean ignoreJspFragmentErrors;

    /**
     * Allows a prefix to be appended to the standard schema locations so that
     * they can be loaded from elsewhere.
     * 
     * @parameter
     */
    private String schemaResourcePrefix;
    
    /**
     * Patterns of jars on the system path that contain tlds. Use | to separate each pattern.
     * 
     * @parameter default-value=".*taglibs[^/]*\.jar|.*jstl-impl[^/]*\.jar$
     */
    private String tldJarNamePatterns;



    /**
     * Should white spaces in template text between actions or directives be trimmed? Defaults to false.
     * @parameter
     */
    private boolean trimSpaces = false;
    

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if (getLog().isInfoEnabled())
        {
            getLog().info("verbose=" + verbose);
            getLog().info("webAppSourceDirectory=" + webAppSourceDirectory);
            getLog().info("generatedClasses=" + generatedClasses);
            getLog().info("webXmlFragment=" + webXmlFragment);
            getLog().info("webXml="+webXml);
            getLog().info("validateXml=" + validateXml);
            getLog().info("packageRoot=" + packageRoot);
            getLog().info("javaEncoding=" + javaEncoding);
            getLog().info("insertionMarker="+ (insertionMarker == null || insertionMarker.equals("") ? END_OF_WEBAPP : insertionMarker));
            getLog().info("keepSources=" + keepSources);
            getLog().info("japcJavaDirectory=" + japcJavaDirectory);
            getLog().info("mergeFragment=" + mergeFragment);
            getLog().info("suppressSmap=" + suppressSmap);
            getLog().info("ignoreJspFragmentErrors=" + ignoreJspFragmentErrors);
            getLog().info("schemaResourcePrefix=" + schemaResourcePrefix);
            getLog().info("trimSpaces=" + trimSpaces);
        }
        try
        {
        	cleanupJavas();
            prepare();
            compile();
            cleanupSrcs();
            mergeWebXml();
            copyJspJavas();
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Failure processing jsps", e);
        }
    }
    
    /**
     * cpoy java to resource dir generated by jspc for servlet debug
     * @throws Exception
     */
	public void copyJspJavas() throws Exception {
		
		if (!keepSources) {
			return;
		}

		String middleDir = packageRoot.replaceAll("\\.", "\\/");
		File fromDir = new File(new File(generatedClasses),middleDir );
		File[] javas = fromDir.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				String fileName = pathname.getName().toLowerCase();
				return fileName.endsWith(".java");
			}
		});

		if (javas == null || javas.length == 0) {
			return;
		}

		for (File fromFile : javas) {
			String fromSuffix = fromFile.getAbsolutePath().substring(generatedClasses.length());
			String fileName = fromFile.getName();
			
			File fromFileParentDir = new File(japcJavaDirectory+"/"+fromSuffix.substring(0, fromSuffix.length()-fileName.length()));
			if(!fromFileParentDir.exists()){
				fromFileParentDir.mkdirs();
			}
			
			File to = new File(new File(japcJavaDirectory), fromSuffix);
			getLog().info(String.format(">>> copyJspJavas from:%s,to:%s", fromFile.getAbsolutePath(), to.getAbsolutePath()));
			
			IO.copy(fromFile, to);
		}
	}

    public void compile() throws Exception
    {
        ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();

        //set up the classpath of the webapp
        List<URL> webAppUrls = setUpWebAppClassPath();
        
        //set up the classpath of the container (ie jetty and jsp jars)
        String sysClassPath = setUpSysClassPath();
        
        //get the list of system classpath jars that contain tlds
        List<URL> tldJarUrls = getSystemJarsWithTlds();
        
        for (URL u:tldJarUrls)
        {
            if (getLog().isDebugEnabled())
                getLog().debug(" sys jar with tlds: "+u);
            webAppUrls.add(u);
        }

      
        //use the classpaths as the classloader
        URLClassLoader webAppClassLoader = new URLClassLoader((URL[]) webAppUrls.toArray(new URL[0]), currentClassLoader);
        StringBuffer webAppClassPath = new StringBuffer();

        for (int i = 0; i < webAppUrls.size(); i++)
        {
            if (getLog().isDebugEnabled())
                getLog().debug("webappclassloader contains: " + webAppUrls.get(i));                
            webAppClassPath.append(new File(webAppUrls.get(i).toURI()).getCanonicalPath());
            if (getLog().isDebugEnabled())
                getLog().debug("added to classpath: " + ((URL) webAppUrls.get(i)).getFile());
            if (i+1<webAppUrls.size())
                webAppClassPath.append(System.getProperty("path.separator"));
        }

        Thread.currentThread().setContextClassLoader(webAppClassLoader);

        JspC jspc = new JspC();
        jspc.setWebXmlFragment(webXmlFragment);
        jspc.setUriroot(webAppSourceDirectory);
        jspc.setPackage(packageRoot);
        jspc.setOutputDir(generatedClasses);
        jspc.setValidateXml(validateXml);
        jspc.setClassPath(webAppClassPath.toString());
        jspc.setCompile(true);
        jspc.setSmapSuppressed(suppressSmap);
        jspc.setSmapDumped(!suppressSmap);
        jspc.setJavaEncoding(javaEncoding);
        jspc.setTrimSpaces(trimSpaces);
        jspc.setSystemClassPath(sysClassPath);
        
        

        // JspC#setExtensions() does not exist, so 
        // always set concrete list of files that will be processed.
        String jspFiles = getJspFiles(webAppSourceDirectory);
        getLog().info("Compiling "+jspFiles);
        getLog().info("Includes="+includes);
        getLog().info("Excludes="+excludes);
        jspc.setJspFiles(jspFiles);
        if (verbose)
        {
            getLog().info("Files selected to precompile: " + jspFiles);
        }
        

        try
        {
            jspc.setIgnoreJspFragmentErrors(ignoreJspFragmentErrors);
        }
        catch (NoSuchMethodError e)
        {
            getLog().debug("Tomcat Jasper does not support configuration option 'ignoreJspFragmentErrors': ignored");
        }

        try
        {
            if (schemaResourcePrefix != null)
                jspc.setSchemaResourcePrefix(schemaResourcePrefix);
        }
        catch (NoSuchMethodError e)
        {
            getLog().debug("Tomcat Jasper does not support configuration option 'schemaResourcePrefix': ignored");
        }
        if (verbose)
            jspc.setVerbose(99);
        else
            jspc.setVerbose(0);

        jspc.execute();

        Thread.currentThread().setContextClassLoader(currentClassLoader);
    }

    private String getJspFiles(String webAppSourceDirectory)
    throws Exception
    {
        List fileNames =  FileUtils.getFileNames(new File(webAppSourceDirectory),includes, excludes, false);
        return StringUtils.join(fileNames.toArray(new String[0]), ",");

    }

    /**
     * clean jspc java for debug
     * @throws Exception
     */
    public void cleanupJavas() throws Exception
    {
        // delete the .java files - depending on keepGenerated setting
            File japcJavaDir = new File(japcJavaDirectory);
           getLog().info(">>>delete japcJavaDir:"+japcJavaDir.getAbsolutePath());
            FileUtils.deleteDirectory(japcJavaDir);
            
            String middleDir = packageRoot.replaceAll("\\.", "\\/");
    		File javacClassDir = new File(new File(generatedClasses),middleDir );
            FileUtils.deleteDirectory(javacClassDir);
           getLog().info(">>>delete javacClassDir:"+javacClassDir.getAbsolutePath());
    }
    
    /**
     * Until Jasper supports the option to generate the srcs in a different dir
     * than the classes, this is the best we can do.
     * 
     * @throws Exception
     */
    public void cleanupSrcs() throws Exception
    {
        // delete the .java files - depending on keepGenerated setting
        if (!keepSources)
        {
            File generatedClassesDir = new File(generatedClasses);

            if(generatedClassesDir.exists() && generatedClassesDir.isDirectory())
            {
                delete(generatedClassesDir, new FileFilter()
                {
                    public boolean accept(File f)
                    {
                        return f.isDirectory() || f.getName().endsWith(".java");
                    }                
                });
            }
        }
    }
    
    static void delete(File dir, FileFilter filter)
    {
        File[] files = dir.listFiles(filter);
        for(int i=0; i<files.length; i++)
        {
            File f = files[i];
            if(f.isDirectory())
                delete(f, filter);
            else
                f.delete();
        }
    }

    /**
     * Take the web fragment and put it inside a copy of the web.xml.
     * 
     * You can specify the insertion point by specifying the string in the
     * insertionMarker configuration entry.
     * 
     * If you dont specify the insertionMarker, then the fragment will be
     * inserted at the end of the file just before the &lt;/webapp&gt;
     * 
     * @throws Exception
     */
    public void mergeWebXml() throws Exception
    {
        if (mergeFragment)
        {
            // open the src web.xml
            File webXml = getWebXmlFile();
           
            if (!webXml.exists())
            {
                getLog().info(webXml.toString() + " does not exist, cannot merge with generated fragment");
                return;
            }

            File fragmentWebXml = new File(webXmlFragment);
            if (!fragmentWebXml.exists())
            {
                getLog().info("No fragment web.xml file generated");
            }
            File mergedWebXml = new File(fragmentWebXml.getParentFile(),
            "web.xml");
            BufferedReader webXmlReader = new BufferedReader(new FileReader(
                    webXml));
            PrintWriter mergedWebXmlWriter = new PrintWriter(new FileWriter(
                    mergedWebXml));

            // read up to the insertion marker or the </webapp> if there is no
            // marker
            boolean atInsertPoint = false;
            boolean atEOF = false;
            String marker = (insertionMarker == null
                    || insertionMarker.equals("") ? END_OF_WEBAPP : insertionMarker);
            while (!atInsertPoint && !atEOF)
            {
                String line = webXmlReader.readLine();
                if (line == null)
                    atEOF = true;
                else if (line.indexOf(marker) >= 0)
                {
                    atInsertPoint = true;
                }
                else
                {
                    mergedWebXmlWriter.println(line);
                }
            }

            // put in the generated fragment
            BufferedReader fragmentWebXmlReader = new BufferedReader(
                    new FileReader(fragmentWebXml));
            IO.copy(fragmentWebXmlReader, mergedWebXmlWriter);

            // if we inserted just before the </web-app>, put it back in
            if (marker.equals(END_OF_WEBAPP))
                mergedWebXmlWriter.println(END_OF_WEBAPP);

            // copy in the rest of the original web.xml file
            IO.copy(webXmlReader, mergedWebXmlWriter);

            webXmlReader.close();
            mergedWebXmlWriter.close();
            fragmentWebXmlReader.close();
        }
    }

    private void prepare() throws Exception
    {
        // For some reason JspC doesn't like it if the dir doesn't
        // already exist and refuses to create the web.xml fragment
        File generatedSourceDirectoryFile = new File(generatedClasses);
        if (!generatedSourceDirectoryFile.exists())
            generatedSourceDirectoryFile.mkdirs();
    }

    /**
     * Set up the execution classpath for Jasper.
     * 
     * Put everything in the classesDirectory and all of the dependencies on the
     * classpath.
     * 
     * @returns a list of the urls of the dependencies
     * @throws Exception
     */
    private List<URL> setUpWebAppClassPath() throws Exception
    {
        //add any classes from the webapp
        List<URL> urls = new ArrayList<URL>();
        String classesDir = classesDirectory.getCanonicalPath();
        classesDir = classesDir + (classesDir.endsWith(File.pathSeparator) ? "" : File.separator);
        urls.add(Resource.toURL(new File(classesDir)));

        if (getLog().isDebugEnabled())
            getLog().debug("Adding to classpath classes dir: " + classesDir);

        //add the dependencies of the webapp (which will form WEB-INF/lib)
        for (Iterator<Artifact> iter = project.getArtifacts().iterator(); iter.hasNext();)
        {
            Artifact artifact = (Artifact)iter.next();

            // Include runtime and compile time libraries
            if (!Artifact.SCOPE_TEST.equals(artifact.getScope()) && !Artifact.SCOPE_PROVIDED.equals(artifact.getScope()))
            {
                String filePath = artifact.getFile().getCanonicalPath();
                if (getLog().isDebugEnabled())
                    getLog().debug("Adding to classpath dependency file: " + filePath);

                urls.add(Resource.toURL(artifact.getFile()));
            }
        }
        return urls;
    }
    
    
    private String setUpSysClassPath () throws Exception
    {
        StringBuffer buff = new StringBuffer();
        
        //Put each of the plugin's artifacts onto the system classpath for jspc
        for (Iterator<Artifact> iter = pluginArtifacts.iterator(); iter.hasNext(); )
        {
            Artifact pluginArtifact = iter.next();
            if ("jar".equalsIgnoreCase(pluginArtifact.getType()))
            {
                if (getLog().isDebugEnabled()) { getLog().debug("Adding plugin artifact "+pluginArtifact);}
                buff.append(pluginArtifact.getFile().getAbsolutePath());
                if (iter.hasNext())
                    buff.append(File.pathSeparator);
            }
        }
        
        
        if (useProvidedScope)
        {
            for ( Iterator<Artifact> iter = projectArtifacts.iterator(); iter.hasNext(); )
            {                   
                Artifact artifact = iter.next();
                if (Artifact.SCOPE_PROVIDED.equals(artifact.getScope()))
                {
                    //test to see if the provided artifact was amongst the plugin artifacts
                    String path = artifact.getFile().getAbsolutePath();
                    if (! buff.toString().contains(path))
                    {
                        if (buff.length() != 0)
                            buff.append(File.pathSeparator);
                        buff.append(path);
                        if (getLog().isDebugEnabled()) { getLog().debug("Adding provided artifact: "+artifact);}
                    }  
                    else
                    {
                        if (getLog().isDebugEnabled()) { getLog().debug("Skipping provided artifact: "+artifact);}
                    }
                }
            }
        }

        return buff.toString();
    }

    
    /**
     * Glassfish jsp requires that we set up the list of system jars that have
     * tlds in them.
     * 
     * This method is a little fragile, as it relies on knowing that the jstl jars
     * are the only ones in the system path that contain tlds.
     * @return
     * @throws Exception
     */
    private List<URL> getSystemJarsWithTlds() throws Exception
    {
        final List<URL> list = new ArrayList<URL>();
        List<URI> artifactUris = new ArrayList<URI>();
        Pattern pattern = Pattern.compile(tldJarNamePatterns);
        for (Iterator<Artifact> iter = pluginArtifacts.iterator(); iter.hasNext(); )
        {
            Artifact pluginArtifact = iter.next();
            artifactUris.add(Resource.newResource(pluginArtifact.getFile()).getURI());
        }
        
        PatternMatcher matcher = new PatternMatcher()
        {
            public void matched(URI uri) throws Exception
            {
                //uri of system artifact matches pattern defining list of jars known to contain tlds
                list.add(uri.toURL());
            }
        };
        matcher.match(pattern, artifactUris.toArray(new URI[artifactUris.size()]), false);
        
        return list;
    }
    
    private File getWebXmlFile ()
    throws IOException
    {
        File file = null;
        File baseDir = project.getBasedir().getCanonicalFile();
        File defaultWebAppSrcDir = new File (baseDir, "src/main/webapp").getCanonicalFile();
        File webAppSrcDir = new File (webAppSourceDirectory).getCanonicalFile();
        File defaultWebXml = new File (defaultWebAppSrcDir, "web.xml").getCanonicalFile();
        
        //If the web.xml has been changed from the default, try that
        File webXmlFile = new File (webXml).getCanonicalFile();
        if (webXmlFile.compareTo(defaultWebXml) != 0)
        {
            file = new File (webXml);
            return file;
        }
        
        //If the web app src directory has not been changed from the default, use whatever
        //is set for the web.xml location
        file = new File (webAppSrcDir, "web.xml");
        return file;
    }
}
