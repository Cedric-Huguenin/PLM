package jlm.core.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import javax.swing.JOptionPane;

import jlm.core.GameListener;
import jlm.core.GameStateListener;
import jlm.core.ProgLangChangesListener;
import jlm.core.StatusStateListener;
import jlm.core.model.lesson.Exercise;
import jlm.core.model.lesson.ExerciseTemplated;
import jlm.core.model.lesson.Lesson;
import jlm.core.ui.MainFrame;
import jlm.universe.Entity;
import jlm.universe.IWorldView;
import jlm.universe.World;

/*
 *  core model which contains all known exercises.
 */
public class Game implements IWorldView {

	private GameState state = GameState.IDLE;

	private final static String LOCAL_PROPERTIES_FILENAME = "jlm.properties";
	private final static String LOCAL_PROPERTIES_SUBDIRECTORY = ".jlm";

	private static Properties defaultGameProperties = new Properties();
	private static Properties localGameProperties = new Properties();
	private static File localGamePropertiesLoadedFile;

	private static Game instance = null;
	private ArrayList<Lesson> lessons = new ArrayList<Lesson>();
	private Lesson currentLesson;
	
	public static final ProgrammingLanguage JAVA = new ProgrammingLanguage("Java");
	public static final ProgrammingLanguage PYTHON = new ProgrammingLanguage("Python");
	public static final ProgrammingLanguage  RUBY = new ProgrammingLanguage("Ruby");
	public static final ProgrammingLanguage  LIGHTBOT = new ProgrammingLanguage("lightbot");
	private static Map<ProgrammingLanguage,String> programmingLanguagesExtensions = null;
	private ProgrammingLanguage programmingLanguage = JAVA;
	
	private List<GameListener> listeners = new ArrayList<GameListener>();
	private World selectedWorld;
	private World answerOfSelectedWorld;
	private World initialOfSelectedWorld;
	private Entity selectedEntity;
	private List<Thread> lessonRunners = new ArrayList<Thread>();
	private List<Thread> demoRunners = new ArrayList<Thread>();
	private boolean sequential = false;
	private ArrayList<GameStateListener> gameStateListeners = new ArrayList<GameStateListener>();

	private LogWriter outputWriter;

	private ISessionKit sessionKit = new ZipSessionKit(this);

	private static boolean ongoingInitialization = false;
	public static Game getInstance() {
		if (Game.instance == null) {
			if (ongoingInitialization) 
				throw new RuntimeException("Loop in initialization process. This is a JLM bug.");
			ongoingInitialization = true;
			Game.instance = new Game();
			ongoingInitialization = false;
		}
		return Game.instance;
	}

	private Game() {
		if (programmingLanguagesExtensions == null) {
			programmingLanguagesExtensions = new HashMap<ProgrammingLanguage, String>();
			programmingLanguagesExtensions.put(JAVA, "java");
			programmingLanguagesExtensions.put(PYTHON, "py");
			programmingLanguagesExtensions.put(RUBY, "rb");
			programmingLanguagesExtensions.put(LIGHTBOT, "ignored");
		}
		Game.loadProperties();
		initLessons();
		loadSession();
	}

	public void initLessons() {
		String lessons = getProperty("jlm.lessons", "");
		if (lessons.equals("")) {
			System.err.println("Error: the property file does not contain any lesson to start. Default to lessons.welcome");
			lessons = "lessons.welcome";
		}
		String[] lessonNames = lessons.split(",");
		for (String name : lessonNames) {
			System.out.println("Load lesson "+name);
			Lesson lesson = null;
			try {
				lesson = (Lesson) Class.forName(name + ".Main").newInstance();
				addLesson(lesson);
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

	public void addLesson(Lesson lesson) {
		this.lessons.add(lesson);
		fireLessonsChanged();
	}

	public List<Lesson> getLessons() {
		return this.lessons;
	}

	public Lesson getCurrentLesson() {
		if (this.currentLesson == null && this.lessons.size() > 0) {
			setCurrentLesson(this.lessons.get(0));
		}
		return this.currentLesson;
	}

	public void setCurrentLesson(Lesson lesson) {
		if (isAccessible(lesson)) {
			this.currentLesson = lesson;
			fireCurrentLessonChanged();
			setCurrentExercise(this.currentLesson.getCurrentExercise());
		}
	}

	// only to avoid that exercise views register as listener of a lesson
	public void setCurrentExercise(Exercise exo) {
		if (exo.getLesson().isAccessible(exo)) {
			this.currentLesson.setCurrentExercise(exo);
			exo.reset();
			fireCurrentExerciseChanged();
			setSelectedWorld(this.currentLesson.getCurrentExercise().getWorld(0));
			
			boolean seenJava=false;
			for (ProgrammingLanguage l:exo.getProgLanguages()) {
				if (l.equals(programmingLanguage)) 
					return; /* The exo accepts the language we currently have */
				if (l.equals(Game.JAVA))
					seenJava = true;
			}
			/* Use java as a fallback programming language (if the exo accepts it)  */
			if (seenJava)
				setProgramingLanguage(Game.JAVA); 
			/* The exo don't like our currently set language, nor Java. Let's pick its first selected language */
			setProgramingLanguage( exo.getProgLanguages()[0] );
			
			MainFrame.getInstance().currentExerciseHasChanged(); // make sure that the right language is selected -- yeah that's a ugly way of doing it
		}
	}

	public boolean isAccessible(Lesson lesson) {
		if (sequential) {
			int index = this.lessons.indexOf(lesson);
			if (index == 0)
				return true;
			if (index > 0)
				return this.lessons.get(index - 1).isSuccessfullyCompleted();
			return false;
		} else {
			return true;
		}
	}

	public World getSelectedWorld() {
		if (this.selectedWorld == null) {
			setSelectedWorld(this.getCurrentLesson().getCurrentExercise().getWorld(0));
		}
		return this.selectedWorld;
	}
	public World[] getSelectedWorlds() {
		World[] res = new World[3];
		res[0] = selectedWorld;
		res[1] = answerOfSelectedWorld;
		res[2] = initialOfSelectedWorld;
		return res;
	}

	public void setSelectedWorld(World world) {
		if (this.selectedWorld != null)
			this.selectedWorld.removeEntityUpdateListener(this);
		this.selectedWorld = world;
		this.selectedWorld.addEntityUpdateListener(this);

		Exercise currentExercise = getCurrentLesson().getCurrentExercise();
		int index = currentExercise.indexOfWorld(this.selectedWorld);
		this.answerOfSelectedWorld = currentExercise.getAnswerOfWorld(index);
		this.initialOfSelectedWorld = currentExercise.getInitialWorld().get(index);
		if (this.selectedWorld.getEntityCount()>0) {
			this.selectedEntity = this.selectedWorld.getEntity(0);
		}
		fireSelectedWorldHasChanged();
	}

	public World getAnswerOfSelectedWorld() {
		return this.answerOfSelectedWorld;
	}

	public void setSelectedEntity(Entity b) {
		this.selectedEntity = b;
		fireSelectedEntityHasChanged();
	}

	public Entity getSelectedEntity() {
		return this.selectedEntity;
	}

	/* Actions of the toolbar buttons */
	private boolean stepMode = false;	
	public void startExerciseExecution() {
		LessonRunner runner = new LessonRunner(Game.getInstance(), this.lessonRunners);
		runner.start();
	}
	public void stopExerciseExecution() {
		while (this.lessonRunners.size() > 0) {
			Thread t = lessonRunners.remove(lessonRunners.size() - 1);
			t.stop(); // harmful but who cares ?
		}
		for (World w : this.currentLesson.getCurrentExercise().getAnswerWorld()) { 
			w.doneDelay();
		}
		setState(GameState.EXECUTION_ENDED);
	}
	public void startExerciseDemoExecution() {
		DemoRunner runner = new DemoRunner(Game.getInstance(), this.demoRunners);
		runner.start();		
	}
	public void startExerciseStepExecution() {
		stepMode = true;
		LessonRunner runner = new LessonRunner(Game.getInstance(), this.lessonRunners);
		runner.start();
	}

	public void enableStepMode() {
		this.stepMode = true;
	}	
	public void disableStepMode() {
		this.stepMode = false;
	}
	
	public boolean stepModeEnabled() {
		return this.stepMode;
	}
	
	public void allowOneStep() {
		for (World w: getCurrentLesson().getCurrentExercise().getCurrentWorld())
			for (Entity e : w.getEntities()) 
				e.allowOneStep();
	}

	public void reset() {
		getCurrentLesson().getCurrentExercise().reset();
		fireCurrentExerciseChanged();
	}

	public void setState(GameState status) {
		this.state = status;
		fireStateChanged(status);
	}

	public GameState getState() {
		return this.state;
	}

	public void setOutputWriter(LogWriter writer) {
		this.outputWriter = writer;
		if (!getProperty("output.capture", "false").equals("true")) {
			Logger l = new Logger(outputWriter);
			System.setOut(l);
			System.setErr(l);
		}
	}

	public LogWriter getOutputWriter() {
		return this.outputWriter;
	}

	public void quit() {
		try {
			// FIXME: this method is not called when pressing APPLE+Q on OSX
			saveSession();
			storeProperties();
			System.exit(0);
		} catch (UserAbortException e) {
			// Ok, user decided to not quit (to get a chance to export the session)
			System.out.println("Exit aborted");
		}
	}

	public void clearSession() {
		this.sessionKit.cleanUp();
		for (Lesson l : this.lessons)
			for (Exercise ex : l.exercises())
				ex.failed();
		fireLessonsChanged();
		fireCurrentExerciseChanged();
	}

	public void loadSession() {
		this.setState(GameState.LOADING);
		this.sessionKit.load();
		this.setState(GameState.LOADING_DONE);
	}

	public void saveSession() throws UserAbortException {
		this.setState(GameState.SAVING);
		this.sessionKit.store();
		this.setState(GameState.SAVING_DONE);
	}

	public ISessionKit getSessionKit() {
		return this.sessionKit;
	}

	public static void loadProperties() {
		InputStream is = null;
		try {
			is = Game.class.getClassLoader().getResourceAsStream("resources/jlm.configuration.properties");
			if (is==null) // try to find the file in the debian package
				is = Game.class.getClassLoader().getResourceAsStream("/etc/jlm.configuration.properties");
			Game.defaultGameProperties.load(is);
		} catch (InvalidPropertiesFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			// resources/jlm.configuration.properties not found. Try jlm.configuration.properties afterward
		} finally {
			if (is != null)
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		String value = Game.defaultGameProperties.getProperty("jlm.configuration.file.path");
		if (value != null) {
			String paths[] = value.split(",");

			for (String localPath : paths) {
				localPath = localPath.replace("$HOME$", System.getProperty("user.home"));
				File localPropertiesFile = new File(localPath + File.separator + Game.LOCAL_PROPERTIES_SUBDIRECTORY,
						Game.LOCAL_PROPERTIES_FILENAME);
				if (localPropertiesFile.exists()) {
					FileInputStream fi = null;
					try {
						fi = new FileInputStream(localPropertiesFile);
						Game.localGameProperties.load(fi);
						Game.localGamePropertiesLoadedFile = localPropertiesFile;
					} catch (InvalidPropertiesFormatException e) {
						e.printStackTrace();
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (fi != null)
							try {
								fi.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
					}
					System.out.println(String.format("Loading properties [%s]", localPropertiesFile));
					break;
				}
			}
		}
	}

	public static void storeProperties() throws UserAbortException {
		FileOutputStream fo = null;
		try {

			// if (Game.localGamePropertiesLoadedFile == null) {

			String value = Game.getProperty("jlm.configuration.file.path");
			if (value != null) {
				String paths[] = value.split(",");

				for (String localPath : paths) {
					localPath = localPath.replace("$HOME$", System.getProperty("user.home"));
					File localPropertiesFileParentDirectory = new File(localPath);
					File localPropertiesFileDirectory = new File(localPath, Game.LOCAL_PROPERTIES_SUBDIRECTORY);

					if (!localPropertiesFileParentDirectory.exists()) {
						continue;
					} else if (localPropertiesFileDirectory.exists() || localPropertiesFileDirectory.mkdir()) {
						Game.localGamePropertiesLoadedFile = new File(localPropertiesFileDirectory,
								Game.LOCAL_PROPERTIES_FILENAME);
						break;
					} else {
						Logger.log("Game:storeProperties", "cannot create local properties store directory ("
								+ localPropertiesFileDirectory + ")");
					}

				}
			} else {
				int choice = JOptionPane.showConfirmDialog(null, 
						"No path provided in the property file (or property file not found)\n"+
						"You may want to export your session with the menu 'Session/Export session'\n" +
						"to save your work manually.\n\n" +
						"Quit without saving?", 
						"Cannot save your changes. Quit without saving?",
						JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE);
				if (choice==JOptionPane.NO_OPTION)
					throw new UserAbortException("Quit aborded by user.");
				return;
			}
			// }
			fo = new FileOutputStream(Game.localGamePropertiesLoadedFile);
			Game.localGameProperties.store(fo, "Java Learning Machine properties");
			System.out.println("Game:storeProperties: properties stored in " + localGamePropertiesLoadedFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fo != null)
				try {
					fo.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}

	public static String getProperty(String key) {
		return Game.getProperty(key, null);
	}

	public static String getProperty(String key, String defaultValue) {
		if (Game.localGameProperties.containsKey(key)) {
			return Game.localGameProperties.getProperty(key);
		} else {
			return Game.defaultGameProperties.getProperty(key, defaultValue);
		}
	}

	public void addGameListener(GameListener l) {
		this.listeners.add(l);
	}

	public void removeGameListener(GameListener l) {
		this.listeners.remove(l);
	}

	protected void fireCurrentLessonChanged() {
		for (GameListener v : this.listeners) {
			v.currentLessonHasChanged();
		}
	}

	protected void fireLessonsChanged() {
		for (GameListener v : this.listeners) {
			v.lessonsChanged();
		}
	}

	protected void fireCurrentExerciseChanged() {
		for (GameListener v : this.listeners) {
			v.currentExerciseHasChanged();
		}
	}

	protected void fireSelectedWorldHasChanged() {
		for (GameListener v : this.listeners) {
			v.selectedWorldHasChanged();
		}
	}

	protected void fireSelectedWorldWasUpdated() {
		for (GameListener v : this.listeners) {
			v.selectedWorldWasUpdated();
		}
	}

	protected void fireSelectedEntityHasChanged() {
		for (GameListener v : this.listeners) {
			v.selectedEntityHasChanged();
		}
	}

	public void addGameStateListener(GameStateListener l) {
		this.gameStateListeners.add(l);
	}

	public void removeGameStateListener(GameStateListener l) {
		this.gameStateListeners.remove(l);
	}

	protected void fireStateChanged(GameState status) {
		for (GameStateListener l : this.gameStateListeners) {
			l.stateChanged(status);
		}
	}

	@Override
	public void worldHasChanged() {
		if (selectedWorld.getEntityCount()>0)
			setSelectedEntity(this.selectedWorld.getEntity(0));
		fireSelectedWorldWasUpdated();
	}

	@Override
	public void worldHasMoved() {
		// don't really care that something moved within the current world
	}

	/* Status bar label changing logic */
	ArrayList<StatusStateListener> statusStateListeners = new ArrayList<StatusStateListener>();
	public void addStatusStateListener(StatusStateListener l) {
		this.statusStateListeners.add(l);
	}
	public void removeStatusStateListener(StatusStateListener l) {
		this.statusStateListeners.remove(l);
	}
	ArrayList<String> statusArgs = new ArrayList<String>();
	String stateTxt = "";
	public void statusRootSet(String txt) {
		stateTxt = txt;
	}
	public void statusArgAdd(String txt) {
		synchronized (statusArgs) {
			statusArgs.add(txt);
			statusChanged();			
		}
	}
	public void statusArgRemove(String txt) {
		synchronized (statusArgs) {
			statusArgs.remove(txt);
			statusChanged();
		}
	}
	public void statusArgEmpty(){
		synchronized (statusArgs) {
			statusArgs.clear();
			statusChanged();
		}
	}
	private void statusChanged() {
		String str = stateTxt;
		boolean first = true;
		for (String s:statusArgs) {
			if (first)
				first = false;
			else
				str += ", ";
			str+= s;
		}
		for (StatusStateListener l : this.statusStateListeners) {
			l.stateChanged(str);
		}
	}
	public void setLocale(String lang) {
		Reader.setLocale(lang);
		for (Lesson lesson : lessons) {
			for (Exercise e:lesson.exercises()) {
				if (e instanceof ExerciseTemplated) {
					((ExerciseTemplated) e).loadHTMLMission();
				}
			}
		}
		setCurrentLesson(getCurrentLesson());
		currentLesson.setCurrentExercise(currentLesson.getCurrentExercise());
	}

	
	
	public void setProgramingLanguage(ProgrammingLanguage newLanguage) {
		if (programmingLanguage.equals(newLanguage))
			return;
		
		if (isValidProgLanguage(newLanguage)) {
			System.out.println("Switch programming language to "+newLanguage);
			this.programmingLanguage = newLanguage;
			fireProgLangChange(newLanguage);
			return;
		}
		throw new RuntimeException("Ignoring request to switch the programming language to the unknown "+newLanguage);
	}

	public static ProgrammingLanguage getProgrammingLanguage() {
		if (ongoingInitialization) /* break an initialization loop -- the crude way (FIXME) */
			return JAVA;
		else 
			return getInstance().programmingLanguage;
	}
	public static String getProgrammingFileExtension() {
		return getProgrammingFileExtension(getProgrammingLanguage());
	}
	public static String getProgrammingFileExtension(ProgrammingLanguage lang) {
		return programmingLanguagesExtensions.get(lang);
	}
	public Set<ProgrammingLanguage> getProgrammingLanguages(){
		return programmingLanguagesExtensions.keySet();
	}

	public boolean isValidProgLanguage(ProgrammingLanguage newL) {
		return programmingLanguagesExtensions.get(newL) != null;
	}
	private List<ProgLangChangesListener> progLangListeners = new Vector<ProgLangChangesListener>();
	public void addProgLangListener(ProgLangChangesListener l) {
		progLangListeners.add(l);
	}
	public void fireProgLangChange(ProgrammingLanguage newLang) {
		for (ProgLangChangesListener l : progLangListeners)
			l.currentProgrammingLanguageHasChanged(newLang);
	}
	public void removeProgLangListener(ProgLangChangesListener l) {
		this.progLangListeners.remove(l);
	}
 
}
