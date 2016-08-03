package risterral;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import risterral.events.AbstractHexEvent;
import risterral.events.CollectionEvent;
import risterral.events.EventParsingException;
import org.apache.commons.io.IOUtils;
import risterral.events.InventoryEvent;
import risterral.events.dto.ItemDTO;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class HexListener implements Runnable {
    private static final List<String> IGNORED_PROMO_LIST = new ArrayList<>(Arrays.asList("Cloud King", "Cloud Queen", "Chest'O Hex",
            "Darad, the Scourgeblade", "Lady Cassandra", "Harvester's Reaper", "Moment of Glory", "Night of Bells", "Ninja Training", "Orson's Dream",
            "Princess Cory", "Prospero, Sylvan Enchanter", "Slithering Marauder", "Spectral Assassin", "Pucid the Matchmaker", 
            "Storm of the Century", "Symeon's Bounty", "The Crowd Roars!", "The Wrath of Zakiir", "Uncle Sparklestaff"));
    private static final String CONTENT_LENGTH = "Content-Length: ";
    private static final String HOST = "Host:";
    private static final String INPUT_GZIP_FILE = "data\\gamedata";
    private static final String GUID_ATTRIBUTE_NAME = "\"m_Id\" : {    \"m_Guid\" : '";
    private static final String EXTENDED_ART_NAME = "ExtendedArt";
    private static final String CARDS_CSV_FILE_NAME = "cards_csv.txt";
    private static final String INVENTORY_CSV_FILE_NAME = "inventory_csv.txt";

    private final ServerSocket serverSocket;
    private Map<String, String> cardNamesMap;
    private Map<String, String> itemsNamesMap;

    public HexListener(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
        prepareDataMapsFromGamedataFile();
    }

    @Override
    public void run() {
        try {
            Socket socket;
            while ((socket = serverSocket.accept()) != null) {
                final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                Integer contentLength = 0;
                while (true) {

                    String line = in.readLine();

                    if (line.contains(CONTENT_LENGTH)) {
                        contentLength = Integer.parseInt(line.substring(CONTENT_LENGTH.length(), line.length()));
                    }

                    if (line.contains(HOST)) {
                        break;
                    }
                }
                in.read(); //10
                in.read(); //13

                byte[] bytes = new byte[contentLength];
                for (int i = 0; i < contentLength; i++) {
                    bytes[i] = (byte) in.read();
                }
                String line = new String(bytes);

                if (line != null) {
                    try {
                        AbstractHexEvent event = AbstractHexEvent.getEvent(line);
                        System.out.println("Successfully parsed event: " + event.getClass().getSimpleName());

                        if (event instanceof CollectionEvent) {
                            processCollectionEvent((CollectionEvent) event);
                        } else if (event instanceof InventoryEvent) {
                            processInventoryEvent((InventoryEvent) event);
                        }
                    } catch (EventParsingException ignored) {
                    }
                }

                out.close();
                in.close();
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void prepareDataMapsFromGamedataFile() {
        String file;
        try {
            GZIPInputStream gzipInputStream = new GZIPInputStream(new FileInputStream(INPUT_GZIP_FILE));
            file = new String(IOUtils.toByteArray(gzipInputStream), "utf-8");
            gzipInputStream.close();
        } catch (Exception e) {
            System.out.println("Gamedata file was not found. Put Hex gamedata file in data folder.");
            return;
        }

        try {
            JsonFactory factory = new JsonFactory();
            ObjectMapper mapper = new ObjectMapper(factory);

            for (String bundle : file.split("\\$\\$\\$---\\$\\$\\$")) {
                if (bundle.isEmpty()) continue;

                String[] jsons = bundle.split("\\$\\$--\\$\\$");
                String dataType = jsons[0].replaceAll("\\s", "");

                if ("CardTemplate".equalsIgnoreCase(dataType)) {
                    cardNamesMap = new HashMap<>();
                    for (int i = 1; i < jsons.length; i++) {
                        try {
                            String guid = getItemGuid(jsons[i].replaceAll("\\n|\\r", ""));
                            ObjectNode cardNode = (ObjectNode) mapper.readTree(jsons[i]
                                    .replaceAll("\\n", "")
                                    .replaceAll("\"m_Guid\" : '\\S+'", "\"m_Guid\" : 0")
                                    .replaceAll(",\\s*}", "}"));

                            if (cardNode.get("m_EquipmentModifiedCard").intValue() != 0) {
                                continue;
                            }

                            String cardName = cardNode.get("m_Name").asText().trim();
                            if (cardNode.get("m_HasAlternateArt").asInt() == 1 && !IGNORED_PROMO_LIST.contains(cardName)) {
                                cardName += " AA";
                            }

                            cardNamesMap.put(guid, cardName);
                        } catch (Exception ignored) {
                        }
                    }
                } else if ("InventoryItemData".equalsIgnoreCase(dataType)) {
                    itemsNamesMap = new HashMap<>();
                    for (int i = 1; i < jsons.length; i++) {
                        try {
                            String guid = getItemGuid(jsons[i].replaceAll("\\n|\\r", ""));
                            ObjectNode itemNode = (ObjectNode) mapper.readTree(jsons[i]
                                    .replaceAll("\\n", "")
                                    .replaceAll("\"m_Guid\" : '\\S+'", "\"m_Guid\" : 0")
                                    .replaceAll("\"m_ReleaseDate\" : '\\S+'", "\"m_ReleaseDate\" : 0")
                                    .replaceAll(",\\s*}", "}"));

                            JsonNode cardPackTypeNode = itemNode.get("m_CardPackType");
                            if (cardPackTypeNode != null && "BoosterPack".equalsIgnoreCase(cardPackTypeNode.asText())) {
                                itemsNamesMap.put(guid, itemNode.get("m_Description").asText().trim());
                            } else {
                                itemsNamesMap.put(guid, itemNode.get("m_Name").asText().trim());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getItemGuid(String content) {
        Integer guidStartIndex = content.indexOf(GUID_ATTRIBUTE_NAME) + GUID_ATTRIBUTE_NAME.length();
        Integer guidEndIndex = content.indexOf("'", guidStartIndex);
        return content.substring(guidStartIndex, guidEndIndex);
    }

    private void processCollectionEvent(CollectionEvent event) {
        if (event.complete == null) {
            return;
        }

        Map<String, Long> cardsMap = new LinkedHashMap<>();
        Map<String, Long> cardsEAMap = new LinkedHashMap<>();
        for (ItemDTO card : event.complete) {
            if (card.guid != null && card.guid.guid != null && !card.guid.guid.isEmpty() && cardNamesMap.containsKey(card.guid.guid)) {
                String cardName = cardNamesMap.get(card.guid.guid);
                Long alreadyIn = 0L;
                if (cardsMap.containsKey(cardName)) {
                    alreadyIn = cardsMap.get(cardName);
                }
                cardsMap.put(cardName, alreadyIn + card.count);
                if (card.flags != null && EXTENDED_ART_NAME.equalsIgnoreCase(card.flags)) {
                    alreadyIn = 0L;
                    if (cardsEAMap.containsKey(cardName)) {
                        alreadyIn = cardsEAMap.get(cardName);
                    }
                    cardsEAMap.put(cardName, alreadyIn + card.count);
                }
            }
        }

        List<String> rows = new ArrayList<>(cardsMap.keySet().size());
        for (Map.Entry<String, Long> cards : cardsMap.entrySet()) {
            String cardNameFixed = cards.getKey().replace(",", "");
            Long numberOfEAs = 0L;
            if (cardsEAMap.containsKey(cards.getKey())) {
                numberOfEAs = cardsEAMap.get(cards.getKey());
            }
            rows.add("\"" + cardNameFixed + "\"," + cards.getValue() + "," + numberOfEAs);
        }
        printRowsToFile(rows, CARDS_CSV_FILE_NAME, "Saved card collection to " + CARDS_CSV_FILE_NAME + " file.");
    }


    private void processInventoryEvent(InventoryEvent event) {
        if (event.complete == null) {
            return;
        }

        List<String> rows = new LinkedList<>();
        for (ItemDTO item : event.complete) {
            if (item.guid != null && item.guid.guid != null && !item.guid.guid.isEmpty() && itemsNamesMap.containsKey(item.guid.guid)) {
                rows.add("\"" + itemsNamesMap.get(item.guid.guid) + "\"," + item.count);
            }
        }
        printRowsToFile(rows, INVENTORY_CSV_FILE_NAME, "Saved inventory content to " + INVENTORY_CSV_FILE_NAME + " file.");
    }

    private void printRowsToFile(List<String> rows, String fileName, String resultInfo) {
        Collections.sort(rows);
        try {
            PrintWriter writer = new PrintWriter(fileName, "UTF-8");
            for (String row : rows) {
                writer.println(row);
            }
            writer.close();
            System.out.println(resultInfo);
        } catch (FileNotFoundException | UnsupportedEncodingException ignored) {
        }
    }
}
