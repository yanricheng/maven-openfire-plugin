package com.reucon.maven.plugin.openfire.utils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.log.Log;

/**
 * 
 * @author yanrc
 * 
 * @goal copy-file
 * @phase pre-integration-test
 * @requiresDependencyResolution compile
 * @description 从某个目录拷贝文件到目标目录
 */
public class FileUtilsMojo extends AbstractMojo {

	/**
	* 要拷贝文件的源码目录
	* 
	* @parameter default-value="${basedir}/target"
	* 
	*/
	private String srcDirOfCopied;

	/**
	 * 要拷贝文件的名称
	 * 
	 * @parameter default-value=""
	 * 
	 */
	private String srcNameOfCopied;

	/**
	 * 要拷贝文件的目标目录
	 * 
	 * @parameter default-value="${basedir}/target"
	 * 
	 */
	private String targetDirOfCopied;

	/**
	 * 目标文件的名称
	 * 
	 * @parameter default-value="-"
	 * 
	 */
	private String spliter;

	/**
	 * 目标文件的名称
	 * 
	 * @parameter default-value=2
	 * 
	 */
	private int index;

	/**
	 * 是否删除源文件
	 * 
	 * @parameter default-value=false
	 * 
	 */
	private boolean deleteSrcFile;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			Log.getLog().info("[>>>copyfile srcDirOfCopied:"+srcDirOfCopied+"]");
			Log.getLog().info("[>>>copyfile srcNameOfCopied:"+srcNameOfCopied+"]");
			Log.getLog().info("[>>>copyfile targetDirOfCopied:"+targetDirOfCopied+"]");
			Log.getLog().info("[>>>copyfile spliter:"+spliter+"]");
			Log.getLog().info("[>>>copyfile index:"+index+"]");
			Log.getLog().info("[>>>copyfile deleteSrcFile:"+deleteSrcFile+"]");
			
			if (StringUtils.isNotEmpty(srcDirOfCopied) && StringUtils.isNotEmpty(targetDirOfCopied)
					&& StringUtils.isEmpty(srcNameOfCopied)) {
				File srcDir = new File(srcDirOfCopied);
				FileUtils.copyDirectory(srcDir, new File(targetDirOfCopied));
				if (deleteSrcFile) {
					FileUtils.deleteDirectory(srcDir);
					getLog().info("删除源目录:" + srcDir.getAbsolutePath());
				}

			} else if (StringUtils.isNotEmpty(srcDirOfCopied) && StringUtils.isNotEmpty(targetDirOfCopied)
					&& StringUtils.isNotEmpty(srcNameOfCopied)) {
				final Pattern p = Pattern.compile(srcNameOfCopied);
				File srcDir = new File(srcDirOfCopied);
				File[] files = srcDir.listFiles(new FileFilter() {
					public boolean accept(File f) {
						return p.matcher( f.getName()).find();
					}
				});

				if (files == null) {
					return;
				}

				for (File f : files) {
					String fileName = f.getName();
					String[] names = fileName.split(spliter);
					String[] fixs = fileName.split("\\.");
					File targetDir = new File(targetDirOfCopied, names[index]);
					if (targetDir.exists()) {
						FileUtils.deleteDirectory(targetDir);
						getLog().info("删除目标目录:" + targetDir.getAbsolutePath());
					}
					//删除目标文件
					File targetFile = new File(targetDirOfCopied, names[index] + "." + fixs[fixs.length - 1]);
					if (targetFile.exists()) {
						targetFile.delete();
						getLog().info("删除目标文件:" + targetFile.getAbsolutePath());
					}

					FileUtils.copyFile(f, targetFile);
					getLog().info("拷贝文件:from:" + f.getAbsolutePath() + ",to:" + targetFile.getAbsolutePath());

					if (deleteSrcFile) {
						IO.delete(f);
						getLog().info("删除源文件:from:" + f.getAbsolutePath());
					}
				}

			}
		} catch (IOException e) {
			getLog().error("copy dir error from:" + srcDirOfCopied + ",to:" + targetDirOfCopied, e);
		}

	}
}
