package plm.core.model.tracking;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import plm.core.model.lesson.ExecutionProgress;
import plm.core.model.lesson.Exercise;

public class GitSpy implements ProgressSpyListener {
	private String username;
	private String filePath;
	private Repository repository;
	private Git git;

	public GitSpy(File path) throws IOException, GitAPIException {
		username = System.getenv("USER");
		if (username == null)
			username = System.getenv("USERNAME");
		if (username == null)
			username = "John Doe";

		filePath = path.getAbsolutePath()
				+ System.getProperty("file.separator") + "repository";

		Git.init().setDirectory(new File(filePath)).call();

		repository = FileRepositoryBuilder.create(new File(filePath, ".git"));

		System.out.println("Created a new repository at "
				+ repository.getDirectory());

		repository.close();

		// get the repository
		git = new Git(repository);
		
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Calendar cal = Calendar.getInstance();
		
		// plm started commit message
		git.commit().setMessage( "PLM started at " + dateFormat.format(cal.getTime()) ).call();
	}

	@Override
	public void executed(Exercise exo) {
		// retrieve the code from the current exercise
		ExecutionProgress lastResult = exo.lastResult;
		String exoCode = exo.getSourceFile(lastResult.language, 0).getBody();

		// create the file
		File exoFile = new File(repository.getDirectory().getParent(),
				exo.getId());
		try {
			exoFile.createNewFile();

			// write the code of the exercise into the file
			FileWriter fw = new FileWriter(exoFile.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(exoCode);
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			// run the add-call
			git.add().addFilepattern(exo.getId()).call();

			System.out.println("Added file " + exoFile + " to repository at "
					+ repository.getDirectory());

			// and then commit the changes
			git.commit().setMessage("Added " + exo.getId()).call();

			System.out.println("Committed file " + exoFile
					+ " to repository at " + repository.getDirectory());
		} catch (GitAPIException e) {
			e.printStackTrace();
		}

		repository.close();
	}

	@Override
	public void switched(Exercise exo) {
		try {
			// run the add-call
			git.branchCreate().setName(exo.getId()).call();
			System.out.println("Created branch " + exo.getId());
		} catch (RefAlreadyExistsException e) {
			// e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}

		try {
			// checkout the branch
			git.checkout().setName(exo.getId()).call();
			System.out.println("Switched to branch " + exo.getId());
		} catch (GitAPIException e) {
			e.printStackTrace();
		}

		repository.close();
	}

	@Override
	public void heartbeat() {
	}

	@Override
	public String join() {
		return "";
	}

	@Override
	public void leave() {
	}

}
