package util;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public class ListUtils {


    /**
     * short circuit evaluation for effectively the statement
     * filter(item0) && filter(item1) && filter(item2) && ...
     * @param list the elements to test the filter on
     * @param filter the function that will be executed on every element
     *               until it returns false for any element or the end of the list is reached
     * @param <T> the type of the elements in the list
     * @return filter(item0) && filter(item1) && filter(item2) && ...
     */
    public static <T extends Object> boolean and(List<T> list, Predicate<T> filter) {
        Iterator<T> iter = list.iterator();
        while(iter.hasNext()) {
            if(!filter.test(iter.next())) return false;
        }
        return true;
    }


    /**
     * short circuit evaluation for effectively the statement
     * filter(item0) || filter(item1) || filter(item2) || ...
     * @param list the elements to test the filter on
     * @param filter the function that will be executed on every element
     *               until it returns false for any element or the end of the list is reached
     * @param <T> the type of the elements in the list
     * @return filter(item0) || filter(item1) || filter(item2) || ...
     */
    public static <T extends Object> boolean or(List<T> list, Predicate<T> filter) {
        Iterator<T> iter = list.iterator();
        while(iter.hasNext()) {
            if(filter.test(iter.next())) return true;
        }
        return false;
    }
}
