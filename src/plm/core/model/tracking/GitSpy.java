package plm.core.model.tracking;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import plm.core.model.Game;
import plm.core.model.lesson.ExecutionProgress;
import plm.core.model.lesson.Exercise;

public class GitSpy implements ProgressSpyListener {
	private String username;
	private String filePath;
	private Repository repository;

	public GitSpy(File path) throws IOException, GitAPIException {
		username = System.getenv("USER");
		if (username == null)
			username = System.getenv("USERNAME");
		if (username == null)
			username = "John Doe";
		
		filePath = path.getAbsolutePath() + System.getProperty("file.separator") + "repository";

        Git.init()
                .setDirectory(new File(filePath))
                .call();

        repository = FileRepositoryBuilder.create(new File(filePath, ".git"));

        System.out.println("Created a new repository at " + repository.getDirectory());

        repository.close();
	}

	@Override
	public void executed(Exercise exo) {		
		// retrieve the code from the current exercise
		ExecutionProgress lastResult = exo.lastResult;
		String exoCode = exo.getSourceFile(lastResult.language, 0)
				.getBody();
		
		// get the repository
		Git git = new Git(repository);

        // create the file
        File exoFile = new File(repository.getDirectory().getParent(), exo.getId());
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
			git.add()
			        .addFilepattern(exo.getId())
			        .call();
			
			System.out.println("Added file " + exoFile + " to repository at " + repository.getDirectory());
			
			// and then commit the changes
			git.commit()
			        .setMessage("Added " + exo.getId())
			        .call();
			
			System.out.println("Committed file " + exoFile + " to repository at " + repository.getDirectory());
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
        
        repository.close();
	}

    @Override
    public void switched(Exercise exo) {}

    @Override
    public void heartbeat() {}

    @Override
    public String join() { return ""; }

    @Override
    public void leave() {
    }

}
