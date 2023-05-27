package fr.ul.miage.reseau.serveur;

import fr.ul.miage.reseau.model.DisqueDur;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
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

    public Serveur(boolean estEsclave) {
        this.estEsclave = estEsclave;
    }

    public static HashMap<String, List<OutputStream>> channels = new HashMap<>();
    private static DisqueDur disqueDur = new DisqueDur();
    public static boolean estEsclave;
    public static Socket esclave;

    public static synchronized void subscribe(String channel, OutputStream out) {
        channels.computeIfAbsent(channel, k -> new ArrayList<>()).add(out);
    }

    public static synchronized void unsubscribe(String channel, OutputStream out) {
        channels.computeIfPresent(channel, (key, value) -> {
            value.remove(out);
            return value.isEmpty() ? null : value;
        });
    }

    public static synchronized void publish(String channel, String message) throws IOException {
        List<OutputStream> outs = channels.get(channel);
        if (outs != null) {
            for (OutputStream out : outs) {
                out.write(("$" + message.length() + "\r\n" + message + "\r\n").getBytes());
            }
        }
    }

    public static void main(String[] args) {
        Serveur serveur = new Serveur(Boolean.parseBoolean(args[0]));
        serveur.run();
    }

    @Override
    public void run() {
        try {
            InetAddress bindAddress = InetAddress.getByName("0.0.0.0");
            int port = 6979;
            if (estEsclave) {
                port = 6980;
            }
            final ServerSocket server = new ServerSocket(port, 1, bindAddress);

            disqueDur = new DisqueDur();
            System.out.println("Serveur démarré sur le port " + port);

            while (true) {
                final Socket client = server.accept();
                if (estEsclave) {
                    esclave = client;
                }

                final Thread thread = new Thread(new GestionUniqueClient(client));
                System.out.println(client.getRemoteSocketAddress().toString());
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class GestionUniqueClient implements Runnable {
        private final Socket client;

        public GestionUniqueClient(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            try {
                boolean start = true;

                // Flux entrant et sortant
                final OutputStream out = client.getOutputStream();
                final InputStream in = client.getInputStream();

                while (start) {
                    final byte[] buffer = new byte[10000];
                    int numRead = in.read(buffer);

                    if (numRead == -1) {
                        break;
                    }
                    final String str = new String(buffer, 0, numRead, StandardCharsets.ISO_8859_1);
                    System.out.println("< " + str);

                    //Traitement de la commande pour la déformater
                    String[] commandes = str.split("\r\n");
                    for (int c = 0; c < commandes.length; c++) {
                        String commande = commandes[c];

                        if (commande.startsWith("*")) {
                            int numArgs = Integer.parseInt(commande.substring(1));
                            String[] args = new String[numArgs];
                            args = commandeToArg(commandes, numArgs);

                            String substr = args[0].toUpperCase();
                            String resultat = "";
                            String channel = "";

                            switch (substr.substring(0, 3)) {
                                case "GET":
                                    if (disqueDur.get(args[1]) != null) {
                                        resultat = "$" + disqueDur.get(args[1]).length() + "\r\n" + disqueDur.get(args[1]) + "\r\n";
                                    } else {
                                        resultat = "$-1\r\n";
                                    }

                                    break;
                                case "SET":
                                    disqueDur.put(args[1], args[2]);
                                    resultat = "+OK\r\n";
                                    // Répliquer la commande SET sur le serveur esclave
                                    replicateCommand(str);
                                    break;
                                case "STR":
                                    if (substr.substring(0, 6).toUpperCase().equals("STRLEN")) {
                                        String key = disqueDur.get(args[1]);
                                        int longueur = key.length();
                                        resultat = ":" + longueur + "\r\n";
                                    }
                                    break;
                                case "APP":
                                    if (substr.substring(0, 6).toUpperCase().equals("APPEND")) {
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
                                    }
                                    break;
                                case "INC":
                                    if (substr.substring(0, 4).toUpperCase().equals("INCR")) {
                                        try {
                                            if (disqueDur.get(args[1]) == null) {
                                                disqueDur.put(args[1], "0");
                                                resultat = ":" + disqueDur.get(args[1]) + "\r\n";
                                            } else {
                                                disqueDur.put(args[1], String.valueOf((Integer.parseInt(disqueDur.get(args[1])) + 1)));
                                                resultat = ":" + (Integer.parseInt(disqueDur.get(args[1]))) + "\r\n";
                                            }
                                        } catch (Exception e) {
                                            resultat = "-ERR n'est pas un int";
                                        }
                                    }
                                    break;
                                case "DEC":
                                    if (substr.substring(0, 4).toUpperCase().equals("DECR")) {
                                        try {
                                            if (disqueDur.get(args[1]) == null) {
                                                disqueDur.put(args[1], "0");
                                                resultat = ":" + disqueDur.get(args[1]) + "\r\n";
                                            } else {
                                                disqueDur.put(args[1], String.valueOf((Integer.parseInt(disqueDur.get(args[1])) - 1)));
                                                resultat = ":" + (Integer.parseInt(disqueDur.get(args[1]))) + "\r\n";
                                            }
                                        } catch (Exception e) {
                                            resultat = "-ERR n'est pas un int";
                                        }
                                    }
                                    break;
                                /*case "KEY":
                                    if (substr.substring(0, 6).toUpperCase().equals("KEYS *")) {
                                        resultat = "*1\r\n$" + disqueDur.size() + "\r\n";
                                    } else if (substr.substring(0, 4).toUpperCase().equals("KEYS")) {
                                        resultat = "*1\r\n$" + disqueDur.keySet().size() + "\r\n";
                                    }
                                    break;*/
                                case "PUB":
                                    if (substr.substring(0, 6).toUpperCase().equals("PUBLISH")) {
                                        channel = args[1];
                                        String message = args[2];
                                        publish(channel, message);
                                        resultat = "+" + channels.get(channel).size() + "\r\n";
                                    }
                                    break;
                                case "SUB":
                                    if (substr.substring(0, 3).toUpperCase().equals("SUB")) {
                                        channel = args[1];
                                        subscribe(channel, out);
                                        resultat = "+" + channels.get(channel).size() + "\r\n";
                                    }
                                    break;
                                case "UNS":
                                    if (substr.substring(0, 3).toUpperCase().equals("UNS")) {
                                        channel = args[1];
                                        unsubscribe(channel, out);
                                        resultat = "+" + channels.get(channel).size() + "\r\n";
                                    }
                                    break;
                                /*case "DEL":
                                    if (substr.substring(0, 3).toUpperCase().equals("DEL")) {
                                        int nombre = disqueDur.remove(args[1]);
                                        resultat = ":" + nombre + "\r\n";
                                    }
                                    break;*/
                                case "EXI":
                                    if (substr.substring(0, 4).toUpperCase().equals("EXIT")) {
                                        start = false;
                                        resultat = "+OK\r\n";
                                    }
                                    break;
                                default:
                                    resultat = "-ERR Unknown command '" + substr + "'\r\n";
                            }

                            // Envoi de la réponse au client
                            if (!estEsclave) {
                                out.write(resultat.getBytes(StandardCharsets.ISO_8859_1));
                                out.flush();
                            } else {
                                // Envoyer la réponse au serveur maître
                                out.write(resultat.getBytes(StandardCharsets.ISO_8859_1));
                                out.flush();

                            }
                        }
                    }
                }

                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static void replicateCommand(String args) throws IOException {
            Socket slaveSocket = new Socket("0.0.0.0", 6980); // Adresse et port du serveur esclave
            OutputStream slaveOut = slaveSocket.getOutputStream();

            // Construction de la commande à répliquer
            StringBuilder commandBuilder = new StringBuilder();
            /*commandBuilder.append("*").append(args.length).append("\r\n");
            for (String arg : args) {
                commandBuilder.append("$").append(arg.length()).append("\r\n").append(arg).append("\r\n");
            }
            String command = commandBuilder.toString();*/

            // Envoyer la commande au serveur esclave
            slaveOut.write(args.getBytes(StandardCharsets.ISO_8859_1));
            slaveOut.flush();

            // Fermer la connexion avec le serveur esclave
            slaveSocket.close();
        }


        private static String[] commandeToArg(String[] commandes, int numArgs) {
            String[] args = new String[numArgs];
            int c = 0;
            int a = 0;
            while (c < commandes.length && a < numArgs) {
                String commande = commandes[c];

                if (!commande.startsWith("*") && !commande.startsWith("$")) {
                    args[a] = commande;
                    a++;
                }
                c++;
            }
            return args;
        }
    }
}