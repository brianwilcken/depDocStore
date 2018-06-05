package geoparsing;

import java.io.IOException;
import java.util.List;

import com.bericotech.clavin.ClavinException;
import com.bericotech.clavin.GeoParser;
import com.bericotech.clavin.GeoParserFactory;
import com.bericotech.clavin.nerd.StanfordExtractor;
import com.bericotech.clavin.resolver.ResolvedLocation;
import common.Tools;

public class LocationResolver {
    private GeoParser parser;

    public LocationResolver() {
        try {
            parser = GeoParserFactory.getDefault(Tools.getProperty("geonamesIndex.location"),
                    new StanfordExtractor("english.all.3class.caseless.distsim.crf.ser.gz", "english.all.3class.caseless.distsim.prop"),
                    1, 1, false);
        } catch (ClavinException e) {
            parser = null;
        } catch (IOException e) {
            parser = null;
        } catch (ClassNotFoundException e) {
            parser = null;
        }
    }

    public List<ResolvedLocation> resolveLocations(String text) {
        try {
            List<ResolvedLocation> resolvedLocations = parser.parse(text);

            return resolvedLocations;
        } catch (Exception e) {
            return null;
        }
    }
}
