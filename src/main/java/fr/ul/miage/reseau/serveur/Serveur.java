package fr.ul.miage.reseau.serveur;

import fr.ul.miage.reseau.model.DisqueDur;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class Serveur implements Runnable {

    public static HashMap<String, List<OutputStream>> channels = new HashMap<>();
    private static DisqueDur disqueDur = new DisqueDur();

    // Autres fonction du serveur
    public static synchronized void subscribe(String channel, OutputStream out)
    {
        channels.computeIfAbsent(channel, k -> new ArrayList<>()).add(out);
    }

    public static synchronized void unsubscribe(String channel, OutputStream out)
    {
        channels.computeIfPresent(channel, (key, value) -> {
            value.remove(out);
            return value.isEmpty() ? null : value;
        });
    }

    public static synchronized void publish(String channel, String message) throws IOException
    {
        List<OutputStream> outs = channels.get(channel);
        if (outs != null) {
            for (OutputStream out : outs) {
                out.write(("$" + message.length() + "\r\n" + message + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    //
   public static void main(String[] args) {
        (new Serveur()).run();
    }

    @Override
    public void run() {
        try {
            final InetAddress bindAddress = InetAddress.getByName("0.0.0.0");
            final ServerSocket server = new ServerSocket(6379, 1, bindAddress);
            disqueDur = new DisqueDur();
            System.out.println("Serveur démarré.");

            while (true) {
                final Socket client = server.accept();
                final Thread thread = new Thread(new GestionUniqueClient(client));
                System.out.println(client.getRemoteSocketAddress().toString());
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private record GestionUniqueClient(Socket client) implements Runnable {

        @Override
            public void run() {
                try {
                    boolean start = true;

                    // Flux entrant et sortant
                    final OutputStream out = client.getOutputStream();
                    final InputStream in = client.getInputStream();

                    while (start) {
                        final byte[] buffer = new byte[1024];
                        int numRead = in.read(buffer);

                        if (numRead == -1) {
                            break;
                        }
                        final String str = new String(buffer, 0, numRead, StandardCharsets.UTF_8);
                        System.out.println("< " + str);

                        //Traitement de la commande pour la déformater
                        String[] commandes = str.split("\r\n");
                        for (String commande : commandes) {
                            //$2 ==> elle est composé de 3 partieS
                            if (commande.startsWith("*")) {
                                int numArgs = Integer.parseInt(commande.substring(1));
                                String[] args;
                                args = commandeToArg(commandes, numArgs);

                                String substr = args[0].toUpperCase();
                                String resultat;
                                //Pour la méthode globale
                                String channel;

                                //Gestion des actions des commands
                                if (substr.length() > 2) {
                                    switch (substr.toUpperCase().substring(0, 3)) {
                                        case "GET" -> {
                                            if (args.length < 2 || disqueDur.get(args[1]) == null) {
                                                resultat = "-ERR il manque un paramètre ou la clé n'existe pas\r\n";
                                            } else {
                                                resultat = "$" + disqueDur.get(args[1]).length() + "\r\n" + disqueDur.get(args[1]) + "\r\n";
                                            }
                                        }
                                        case "SET" -> {
                                            if (args.length < 3) {
                                                resultat = "-ERR il manque un ou plusieurs paramètres pour la commande\r\n";
                                            } else {
                                                if (disqueDur.exist(args[1])) disqueDur.del(args[1]);
                                                disqueDur.put(args[1], args[2]);
                                                resultat = "+OK\r\n";
                                            }
                                        }
                                        case "STR" -> {
                                            if (substr.length() == 6 && Objects.equals(substr.substring(0, 6).toUpperCase(), "STRLEN")) {
                                                if (disqueDur.exist(args[1])) {

                                                    String key = disqueDur.get(args[1]);
                                                    int longueur = key.length();
                                                    resultat = ":" + longueur + "\r\n";
                                                } else {
                                                    resultat = "-ERR la clé que vous avez entré n'existe pas sur le serveur\r\n";
                                                }
                                            } else {
                                                resultat = "-ERR la commande n'existe pas\r\n";
                                            }
                                        }
                                        case "APP" -> {
                                            //TODO prendre en compte qu'il y a 2 paramètres
                                            if (substr.length() == 6 && Objects.equals(substr.substring(0, 6).toUpperCase(), "APPEND")) {
                                                String key = args[1];
                                                String value = args[2];

                                                if (disqueDur.containsKey(key) && disqueDur.get(key) instanceof String) {
                                                    String ancienneValeur = disqueDur.get(key);
                                                    String nouvelleValeur = ancienneValeur.concat(value);
                                                    disqueDur.put(key, nouvelleValeur);
                                                    resultat = ":" + nouvelleValeur.length() + "\r\n";
                                                } else {
                                                    disqueDur.put(key, value);
                                                    resultat = ":" + value.length() + "\r\n";
                                                }
                                            } else {
                                                resultat = "-ERR la commande n'existe pas\r\n";
                                            }
                                        }
                                        case "INC" -> {
                                            if (substr.length() == 4 && substr.substring(0, 4).toUpperCase().equals("INCR")) {
                                                try {
                                                    //gérer le cas clé n'existe pas --> set à 0
                                                    if (disqueDur.get(args[1]) == null) {
                                                        disqueDur.put(args[1], "0");
                                                        resultat = ":" + disqueDur.get(args[1]) + "\r\n";
                                                    } else {
                                                        disqueDur.put(args[1], String.valueOf((Integer.parseInt(disqueDur.get(args[1])) + 1)));
                                                        resultat = ":" + (Integer.parseInt(disqueDur.get(args[1]))) + "\r\n";
                                                    }
                                                } catch (Exception e) {
                                                    resultat = "-ERR n'est pas un int\r\n";
                                                }
                                            } else {
                                                resultat = "-ERR la commande n'existe pas\r\n";
                                            }
                                        }
                                        case "DEC" -> {
                                            if (substr.length() == 4 && substr.substring(0, 4).toUpperCase().equals("DECR")) {
                                                try {
                                                    //gérer le cas clé n'existe pas --> set à 0
                                                    if (disqueDur.get(args[1]) == null) {
                                                        disqueDur.put(args[1], "0");
                                                        resultat = ":" + disqueDur.get(args[1]) + "\r\n";
                                                    } else {
                                                        disqueDur.put(args[1], String.valueOf((Integer.parseInt(disqueDur.get(args[1])) - 1)));
                                                        resultat = ":" + (Integer.parseInt(disqueDur.get(args[1]))) + "\r\n";
                                                    }
                                                } catch (Exception e) {
                                                    resultat = "-ERR n'est pas un int\r\n";
                                                }
                                            } else {
                                                resultat = "-ERR la commande n'existe pas\r\n";
                                            }
                                        }
                                        case "DEL" -> {
                                        /*
                                            on peut avoir plusieurs arguments (key)
                                         */
                                            int nb = 0;
                                            for (int i = 1; i < args.length; i++) {
                                                if (disqueDur.del(args[i])) {
                                                    nb++;
                                                }
                                            }
                                            resultat = ":" + nb + "\r\n";
                                        }
                                        case "EXI" -> {
                                            if (substr.length() == 6 && Objects.equals(substr.substring(0, 6).toUpperCase(), "EXISTS")) {
                                            /*
                                                on peut avoir plusieurs arguments (key)
                                             */
                                                int nbExist = 0;

                                                for (String currentKey : args) {
                                                    if (disqueDur.exist(currentKey)) {
                                                        nbExist++;
                                                    }
                                                }
                                                resultat = ":" + nbExist + "\r\n";
                                            } else {
                                                resultat = "-ERR la commande n'existe pas\r\n";
                                            }
                                        }
                                        case "EXP" -> {
                                            //TODO prendre en compte qu'il y a 2 paramètres
                                            if (substr.length() == 6 && Objects.equals(substr.substring(0, 6).toUpperCase(), "EXPIRE")) {
                                                if (disqueDur.get(args[1]) == null || args.length < 3) {
                                                    resultat = "-ERR la clé n'existe pas ou il manque des arguements\r\n";
                                                } else {
                                                    String key = args[1];
                                                    String second = args[2];
                                                    disqueDur.ExpireDuration(key, second);
                                                    resultat = "+OK\r\n";
                                                }
                                            } else {
                                                resultat = "-ERR la commande n'existe pas\r\n";
                                            }
                                        }
                                        case "SUB" -> {
                                            if (Objects.equals(substr.substring(0, 9).toUpperCase(), "SUBSCRIBE")) {
                                                channel = args[1];
                                                subscribe(channel, out);
                                                resultat = "+OK\r\n";
                                            } else {
                                                resultat = "-ERR la commande n'existe pas\r\n";
                                            }
                                        }
                                        case "PUB" -> {
                                            if (substr.length() == 7 && Objects.equals(substr.substring(0, 7).toUpperCase(), "PUBLISH")) {
                                                channel = args[1];
                                                String message = args[2];
                                                publish(channel, message);
                                                resultat = "+OK\r\n";
                                            } else {
                                                resultat = "-ERR la commande n'existe pas\r\n";
                                            }
                                        }
                                        case "UNS" -> {
                                            if (substr.length() == 11 && Objects.equals(substr.substring(0, 11).toUpperCase(), "UNSUBSCRIBE")) {
                                                channel = args[1];
                                                unsubscribe(channel, out);
                                                resultat = "+OK\r\n";
                                            } else {
                                                resultat = "-ERR la commande n'existe pas\r\n";
                                            }
                                        }
                                        case "QUI" -> {
                                            if (substr.length() == 4 && Objects.equals(substr.substring(0, 4).toUpperCase(), "QUIT")) {
                                                start = false;
                                                resultat = "+OK\r\n";
                                            } else {
                                                resultat = "-ERR la commande n'existe pas\r\n";
                                            }
                                        }
                                        default -> resultat = "-ERR Commande pas valide\r\n";
                                    }
                                } else {
                                    resultat = "-ERR la chaine est trop petite il faut moins 3 charactères  \r\n";
                                }
                                out.write((resultat).getBytes(StandardCharsets.UTF_8));
                            }
                        }
                    }

                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }

            private String[] commandeToArg(String[] commandes, int num) {
                String[] resultat = new String[num];
                int i = 0;
                for (String str :
                        commandes) {
                    if (!(str.startsWith("$") || str.startsWith("*"))) {
                        resultat[i] = str;
                        i++;
                    }
                }
                return resultat;
            }
        }
}