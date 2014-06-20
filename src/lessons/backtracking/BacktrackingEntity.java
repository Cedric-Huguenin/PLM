package lessons.backtracking;

import java.io.BufferedWriter;

import plm.universe.Entity;

public class BacktrackingEntity extends Entity {
	@Override
	public void run() {
		run((BacktrackingPartialSolution) world.getParameter(0));
		System.out.println("Solution:"+((BacktrackingWorld) world).bestSolution);
	}
	
	protected void run(BacktrackingPartialSolution solution) {
		throw new RuntimeException(this.getClass().getCanonicalName()+": This method should be overriden! Please go fix your lesson.");
	}
	
	/* World logic */
	
	public void newBestSolution(BacktrackingPartialSolution solution) {
		((BacktrackingWorld) world).newBestSolution(solution);
	}

	protected BacktrackingPartialSolution getBestSolution() {
		return ((BacktrackingWorld) world).getBestSolution();
	}

	@Override
	protected void command(String command, BufferedWriter out) {
		// TODO GIANNINI
		
	}
}
