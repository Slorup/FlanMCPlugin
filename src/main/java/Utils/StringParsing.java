package Utils;


import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StringParsing {
    public static ArrayList<String> configStringToList(String fromConfig){
        if(fromConfig == null)
            return new ArrayList<String>();
        fromConfig = fromConfig.replace("[", "").replace("]", "");
        return new ArrayList<>(Arrays.asList(fromConfig.split(",")));
    }

    public static String listToConfigString(List<String> l){
        if(l.size() == 0)
            return "";
        StringBuilder str = new StringBuilder("[");

        for (Object o : l) str.append(o.toString()).append(",");
        str.deleteCharAt(str.length()-1).append("]");

        return str.toString();
    }

    public static Triple<Integer, Integer, Integer> getCoordsFromConfigLocation(String loc){
        loc = loc.replace("(","").replace(")","");
        String[] parts = loc.split(";");
        if(parts.length < 3) return null;
        return new Triple<Integer, Integer, Integer>(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
    }

    public static Location getLocFromConfigLocation(String loc){
        loc = loc.replace("(","").replace(")","");
        String[] parts = loc.split(";");
        if(parts.length < 3) return null;
        return new Location(Bukkit.getWorlds().get(0), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
    }
}
