package us.kbase.auth.client.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches strings with an expiry time.
 * 
 * Strings are stored until the size of the cache is greater than the maximum
 * allowed size. Strings are then ordered by most recent access and the oldest
 * strings are discarded to return the cache to its nominal size.
 * 
 * This class is thread safe.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class StringCache {
	// is there some way to share code with tokencache? Doesn't seem so...
	
	// TODO CODE replace this with Caffeine.
	
	/**
	 * Default expiry time for strings.
	 */
	final public static long EXPIRY = 24 * 60 * 60;
	
	final private static int ADDED = 0;
	final private static int TOUCHED = 1;
	
	final private int size;
	final private int maxsize;
	private long expiry = EXPIRY;
	final private ConcurrentHashMap<String, List<Date>> cache;
	
	/**
	 * Create a new StringCache.
	 * @param size the nominal size of the cache in strings, which must be &lt; maxsize
	 * @param maxsize the maximum size of the cache in strings
	 */
	public StringCache(int size, int maxsize) {
		if (size < 1 || maxsize < 1) {
			throw new IllegalArgumentException("size and maxsize must be > 0");
		}
		if (size >= maxsize) {
			throw new IllegalArgumentException("size must be < maxsize");
		}
		this.size = size;
		this.maxsize = maxsize;
		this.cache = new ConcurrentHashMap<String, List<Date>>(maxsize);
	}
	
	/**
	 * Set the lifetime of a string in the cache.
	 * @param seconds the lifetime of a string.
	 */
	public void setExpiry(final long seconds) {
		if (seconds < 1) {
			throw new IllegalArgumentException("seconds must be > 0");
		}
		expiry = seconds;
	}
	
	/**
	 * Get the lifetime of a string in the cache.
	 * @return the lifetime of a string.
	 */
	public long getExpiry() {
		return expiry;
	}
	
	/**
	 * Determine whether a string is in the cache. Does not reset the
	 * expiration time for the string.
	 * @param string the string to check
	 * @return <code>true</code> if the string is in the cache, <code>false</code>
	 * otherwise.
	 */
	public boolean hasString(String string) {
		if (string == null) {
			throw new NullPointerException("string cannot be null");
		}
		if (!cache.containsKey(string)) {
			return false;
		}
		if (new Date().getTime() - cache.get(string).get(ADDED).getTime() > expiry * 1000) {
			return false;
		}
		final List<Date> dates = new ArrayList<Date>(2);
		dates.add(ADDED, cache.get(string).get(ADDED));
		dates.add(TOUCHED, new Date());
		cache.put(string, dates);
		return true;
	}
		
	/**
	 * Add a string to the cache. Resets the expiration time for the string.
	 * @param string the string to add
	 */
	public void putString(String string) {
		if (string == null) {
			throw new NullPointerException("string cannot be null");
		}
		final Date now = new Date();
		final List<Date> dates = new ArrayList<Date>(2);
		dates.add(ADDED, now);
		dates.add(TOUCHED, now);
		cache.put(string, dates);
		synchronized (cache) { // block here otherwise all threads may start sorting
			if (cache.size() <= maxsize) {return;}
			final List<DateString> ds = new ArrayList<DateString>();
			for (final String s: cache.keySet()) {
				ds.add(new DateString(cache.get(s).get(TOUCHED), s));
			}
			Collections.sort(ds);
			for(int i = size; i < ds.size(); i++) {
				cache.remove(ds.get(i).string);
			}
		}
	}
}

class DateString implements Comparable<DateString>{

	final String string;
	final Date date;
	
	DateString(Date date, String string) {
		this.string = string;
		this.date = date;
	}
	
	@Override
	public int compareTo(DateString ds) {
		return - this.date.compareTo(ds.date); //descending
	}
}
