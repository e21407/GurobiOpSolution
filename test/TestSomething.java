import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestSomething {

    @Test
    public void emptyMapTest(){
        Map<String, List<Integer>> map = new HashMap<>();
        List<Integer> integers = map.get("1");
        if (null == integers){
            integers = new ArrayList<>();
            map.put("1", integers);
            integers.add(123);

        }
        System.out.println(map.get("1").get(0));

    }
}
