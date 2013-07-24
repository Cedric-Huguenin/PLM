package lessons.sort.baseball.universe;

import jlm.universe.Entity;
import jlm.universe.World;

/**
 * @see BaseballWorld
 */
public class BaseballEntity extends Entity {
	public BaseballEntity() {
		super("Baseball Entity");
	}

	public BaseballEntity(String name) {
		super(name);
	}

	public BaseballEntity(String name, World world) {
		super(name,world);
	}

	@Override
	public Entity copy() {
		return new BaseballEntity(this.name);
	}

	/** Returns the amount of bases on your field */
	public int getAmountOfBases() {
		return ((BaseballWorld) this.world).getBasesAmount();
	}

	/** Returns the color of the specified base */
	public int getBaseColor( int baseIndex) {
		return  ((BaseballWorld) world).getBaseColor(baseIndex);
	}

	/** Returns the base in which the hole is located */
	public int getHoleBase() {
		return ((BaseballWorld) this.world).getHoleBase();
	}

	/** Returns the hole position within its base */
	public int getHolePosition(){
		return ((BaseballWorld) this.world).getHolePosition();
	}

	/** Returns the amount of players locations available on each base of the field */
	public int getLocationsAmount() {
		return ((BaseballWorld) this.world).getLocationsAmount();
	}

	/** Returns the color of the player at the specified coordinate */
	public int getPlayerColor(int base, int position) {
		return ((BaseballWorld) this.world).getPlayerColor(base,position);
	}

	/** Returns whether every players of the specified base are at home */
	public boolean isBaseSorted(int baseIndex) {
		return ((BaseballWorld) this.world).getBase(baseIndex).isSorted();
	}

	/**
	 * Move the specified player to the hole
	 * @throws IllegalArgumentException if the specified player is not near the hole (at most one base away) 
	 */
	public void move(int base, int position) {
		((BaseballWorld) this.world).move(base,position);
		stepUI();
	}

	/** Must exist so that exercises can instantiate the entity (Entity is abstract) */ 
	@Override
	public void run() {
	}

	/**
	 * Return a string representation of the world
	 * @return A string representation of the world
	 */
	public String toString(){
		return "BaseballEntity (" + this.getClass().getName() + ")";
	}
}
