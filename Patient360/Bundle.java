import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONObject;

import org.json.simple.JSONArray;

public class Bundle {
    private JSONObject bundle;
    private JSONArray entryArray;
    long total;
    
    Bundle(String json) throws ParseException {
        JSONParser  jsonParser = new JSONParser();
        bundle = (JSONObject) jsonParser.parse(json);
        entryArray = (JSONArray)bundle.get("entry");
        total = (long)bundle.get("total");
    }

    JSONObject getEntryResource(int i) {
        return (JSONObject)((JSONObject)entryArray.get(i)).get("resource");
    }

    String getEntryResourceId(int i) {
        JSONObject entry = (JSONObject)((JSONObject)entryArray.get(i)).get("resource");
        return (String)entry.get("id");
    }

    String getEntryAddressZip(int i) {
        JSONObject entry = (JSONObject)((JSONObject)entryArray.get(i)).get("resource");
        JSONObject address = (JSONObject)((JSONArray)entry.get("address")).get(0);
        return (String)address.get("postalCode");
    }

    public String getBatchBundle() {
        JSONObject batch = (JSONObject)bundle.clone();
        batch.put("type", "batch");
        batch.remove("total");
        batch.remove("meta");
        batch.remove("link");
//        batchBundle.put("fullUrl", "urn:uuid:UUIDV4");
        return batch.toJSONString();
    }
}
