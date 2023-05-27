package fr.ul.miage.reseau.serveur;

import fr.ul.miage.reseau.model.DisqueDur;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Serveur implements Runnable {


    private final int port;
    private static String bindAdress = "0.0.0.0";

    public Serveur(int port){
        this.port = port;
    }

    public static HashMap<String, List<OutputStream>> channels = new HashMap<>();
    private static DisqueDur disqueDur = new DisqueDur();
    private static boolean master = false;



    private static HashMap<Integer, String> masterCommandList = new HashMap<>();
    private static Integer numCommand = 1;


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
                out.write(("$" + message.length() + "\r\n" + message + "\r\n").getBytes());
            }
        }
    }

    private static synchronized void addMasterCommand(String command){
        if(master){
            System.out.println("ajout commande: "+command);
            masterCommandList.put(numCommand,command);
            numCommand++;
        }
    }

    private static synchronized void connectionMaster(int port, String bindAddress){
        final ServerSocket server;
        try {
            server = new ServerSocket(port, 1, InetAddress.getByName(bindAddress));
            disqueDur = new DisqueDur();

            while (true) {
                final Socket client = server.accept();
                final Thread thread = new Thread(new GestionUniqueClient(client));
                System.out.println(client.getRemoteSocketAddress().toString());
                thread.start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static String getBindAdress() {
        return bindAdress;
    }

    public synchronized HashMap<Integer, String> getMasterCommandList() {
        return masterCommandList;
    }

    public synchronized DisqueDur getDisqueDur(){
        return disqueDur;
    }

   public static void main(String[] args) {
        (new Serveur(args[0]!=null?Integer.parseInt(args[0]):6379) ).run(); //regarde si il y a un argument de saisie (le port)
       //s'il n'y en a pas, on met le port 6379
    }

    @Override
    public void run() {
        try {
            final InetAddress bindAddress = InetAddress.getByName(getBindAdress());
            final ServerSocket server = new ServerSocket(port, 1, bindAddress);

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

                while(start) {
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

                        //$2 ==> elle est composé de 3 partieS
                        if (commande.startsWith("*")) {
                            int numArgs = Integer.parseInt(commande.substring(1));
                            String[] args = new String[numArgs];
                            args = commandeToArg(commandes, numArgs);

                            String substr = args[0].toUpperCase();
                            String resultat = "";
                            //Pour la méthode globale
                            String channel = "";


                            //Gestion des actions des commands
                            if(substr.length()>2) {
                                switch (substr.toUpperCase().substring(0, 3)) {
                                    case "GET":
                                        System.out.println(args.length);
                                        if (args.length < 2 || disqueDur.get(args[1]) == null) {
                                            resultat = "-ERR il manque un paramètre ou la clé n'existe pas\r\n";
                                        } else {
                                            addMasterCommand(str);
                                            resultat = "$" + disqueDur.get(args[1]).length() + "\r\n" + disqueDur.get(args[1]) + "\r\n";
                                        }
                                        break;
                                    case "SET":
                                        System.out.println("a" + args.length);
                                        if (args.length < 3 || disqueDur.exist(args[1])) {
                                            resultat = "-ERR il manque un ou plusieurs paramètre pour la commande ou la clé existe déjà\r\n";
                                        } else {
                                            disqueDur.put(args[1], args[2]);
                                            addMasterCommand(str);
                                            resultat = "+OK\r\n";
                                        }

                                        break;
                                    case "STR":
                                        if (substr.length() == 6 && Objects.equals(substr.substring(0, 6).toUpperCase(), "STRLEN")) {
                                            if (disqueDur.exist(args[1])) {

                                                String key = disqueDur.get(args[1]);
                                                int longueur = key.length();
                                                resultat = ":" + longueur + "\r\n";
                                                addMasterCommand(str);
                                            } else {
                                                resultat = "-ERR la clé que vous avez entré n'existe pas sur le serveur\r\n";
                                            }
                                        } else {
                                            resultat = "-ERR la commande n'existe pas\r\n";
                                        }
                                        break;
                                    case "APP":
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
                                            addMasterCommand(str);
                                        } else {
                                            resultat = "-ERR la commande n'existe pas\r\n";
                                        }
                                        break;
                                    case "INC":
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
                                                addMasterCommand(str);
                                            } catch (Exception e) {
                                                resultat = "-ERR n'est pas un int\r\n";
                                            }
                                        } else {
                                            resultat = "-ERR la commande n'existe pas\r\n";
                                        }
                                        break;
                                    case "DEC":
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
                                                addMasterCommand(str);
                                            } catch (Exception e) {
                                                resultat = "-ERR n'est pas un int\r\n";
                                            }
                                        } else {
                                            resultat = "-ERR la commande n'existe pas\r\n";
                                        }
                                        break;
                                    case "DEL":
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
                                        addMasterCommand(str);
                                        break;
                                    case "EXI":
                                        if (substr.length() == 6 && !Objects.equals(substr.substring(0, 6).toUpperCase(), "EXISTS")) {
                                            System.out.println("aze");
                                        /*
                                            on peut avoir plusieurs arguments (key)
                                         */
                                            int nbExist = 0;

                                            for (int i = 2; i < args.length; i++) {
                                                String currentKey = args[i];
                                                if (disqueDur.exist(currentKey)) {
                                                    nbExist++;
                                                }
                                            }
                                            resultat = ":" + nbExist + "\r\n";
                                            addMasterCommand(str);
                                        } else {
                                            resultat = "-ERR la commande n'existe pas\r\n";
                                        }
                                        break;
                                    case "EXP":
                                        //TODO prendre en compte qu'il y a 2 paramètres
                                        if (substr.length() == 6 && Objects.equals(substr.substring(0, 6).toUpperCase(), "EXPIRE")) {
                                            if (disqueDur.get(args[1]) == null || args.length < 3) {
                                                resultat = "-ERR la clé n'existe pas ou il manque des arguements\r\n";
                                            } else {
                                                String key = args[1];
                                                String second = args[2];
                                                disqueDur.ExpireDuration(key, second);
                                                resultat = "+OK\r\n";
                                                addMasterCommand(str);
                                            }
                                        } else {
                                            resultat = "-ERR la commande n'existe pas\r\n";
                                        }
                                        break;
                                    case "SUB":
                                        if (Objects.equals(substr.substring(0, 9).toUpperCase(), "SUBSCRIBE")) {
                                            channel = args[1];
                                            subscribe(channel, out);
                                            resultat = "+OK\r\n";
                                            addMasterCommand(str);
                                        } else {
                                            resultat = "-ERR la commande n'existe pas\r\n";
                                        }
                                        break;
                                    case "PUB":
                                        if (substr.length() == 7 && Objects.equals(substr.substring(0, 7).toUpperCase(), "PUBLISH")) {
                                            channel = args[1];
                                            String message = args[2];
                                            publish(channel, message);
                                            resultat = "+OK\r\n";
                                            addMasterCommand(str);
                                        } else {
                                            resultat = "-ERR la commande n'existe pas\r\n";
                                        }
                                        break;
                                    case "UNS":
                                        if (substr.length() == 11 && Objects.equals(substr.substring(0, 11).toUpperCase(), "UNSUBSCRIBE")) {
                                            channel = args[1];
                                            unsubscribe(channel, out);
                                            resultat = "+OK\r\n";
                                            addMasterCommand(str);
                                        } else {
                                            resultat = "-ERR la commande n'existe pas\r\n";
                                        }
                                        break;

                                    case "QUI":
                                        if (substr.length() == 4 && Objects.equals(substr.substring(0, 4).toUpperCase(), "QUIT")) {
                                            start = false;
                                            resultat = "+OK\r\n";
                                            addMasterCommand(str);
                                        } else {
                                            resultat = "-ERR la commande n'existe pas\r\n";
                                        }
                                        break;
                                    case "REP":
                                        if (substr.length() == 9 && Objects.equals(substr.substring(0, 9).toUpperCase(), "REPLICAOF") && args[2]!=null){
                                            if (args[2].toUpperCase().equals("ONE") ) {
                                                //case REPLICAOF NO ONE --> Definit Master
                                                master = true;
                                                resultat = "+OK est Master\r\n";
                                            } else if (args[2].length() == 4){ //vérification normalité format port
                                                // try/catch parsing int pour etre sur que ça soit un port valide
                                                //se connecter a un clien avec le port = args[2]
                                                try {
                                                    int port = Integer.parseInt(args[2]);
                                                    Serveur s = new Serveur(port);
                                                    //s.run();.

                                                    System.out.println("Test aff disque dur: ");
                                                    s.getDisqueDur().display();

                                                    //executer toutes les commandes stocké dans le serveur.mapCommandeMaster
                                                    resultat = "+OK blabla\r\n";

                                                } catch (Exception e){
                                                    resultat = "-ERR le port saisit est invalide\r\n";
                                                }
                                            } else {
                                                resultat = "-ERR argument(s) invalide\r\n";
                                            }
                                        } else {
                                            resultat = "-ERR la commande n'existe pas/est invalide\r\n";
                                        }
                                        break;
                                    default:
                                        resultat = "-ERR Commande pas valide\r\n";
                                        break;
                                }
                            }else{
                                resultat = "-ERR la chaine est trop petite il faut moins 3 charactères  \r\n";
                            }
                            System.out.println("test interne: "+masterCommandList.size());
                            out.write((resultat).getBytes());
                        }
                    }
                }

            } catch(IOException e) {
                System.out.println(e.getMessage());
            }
        }



        private String[] commandeToArg(String[] commandes, int num) {
            String[] resultat = new String[num];
            int i = 0;
            for (String str:
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