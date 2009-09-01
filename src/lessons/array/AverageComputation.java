package lessons.array;

import universe.array.ArrayEntity;
import universe.array.ArrayWorld;
import lessons.array.AverageComputation;

public class AverageComputation extends ArrayEntity {

	@Override
	public void run() {
		ArrayWorld w = (ArrayWorld) this.getWorld();
		this.result = this.average(w.getValues());
	}

	/* BEGIN TEMPLATE */
// computes the average value of the values contained in tab variable
public int average(int[] tab) {
	/* BEGIN SOLUTION */
	int sum = 0;
	for (int i=0; i<tab.length; i++) {
		sum += tab[i];
	}
	return sum / tab.length;
	/* END SOLUTION */
}

	/* END TEMPLATE */

}
