package org.json.junit.data;

/**
 * Sample singleton done as an Enum for use with bean testing.
 * 
 * @author John Aylward
 *
 */
public enum SingletonEnum {
    INSTANCE;
    /** */
    private int someInt;
    /** */
    private String someString;

    /** single instance. */

    /**
     * @return the singleton instance. I a real application, I'd hope no one did
     *         this to an enum singleton.
     */
    public static final SingletonEnum getInstance() {
        return INSTANCE;
    }

    /** */
    private SingletonEnum() {
    }

    /** @return someInt */
    public int getSomeInt() {
        return someInt;
    }

    /** sets someInt */
    public void setSomeInt(int someInt) {
        this.someInt = someInt;
    }

    /** @return someString */
    public String getSomeString() {
        return someString;
    }

    /** sets someString */
    public void setSomeString(String someString) {
        this.someString = someString;
    }
}
