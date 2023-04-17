package fr.ul.miage.reseau.serveur;

import fr.ul.miage.reseau.model.DisqueDur;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serveur web minimaliste.
 */
public class Serveur implements Runnable {
    
   public static void main(String[] args) {
        (new Serveur()).run();
    }

    @Override
    public void run() {
        try {
            final InetAddress bindAddress = InetAddress.getByName("0.0.0.0");
            try(final ServerSocket server = new ServerSocket(6379, 1, bindAddress)) {

                System.out.println("Attente de connexion ...");
                final Socket client = server.accept();
                DisqueDur disqueDur = new DisqueDur();
                boolean start = true;

                final OutputStream out = client.getOutputStream();
                final InputStream in = client.getInputStream();

                while(start)
                {
                    final byte[] buffer = new byte[1024];
                    int numRead = in.read(buffer);

                    final String str = new String(buffer, 0, numRead, StandardCharsets.ISO_8859_1);
                    System.out.println("< " + str);

                    String substr = str.substring(0,3);
                    String resultat = new String();
                    //disqueDur.display();
                    switch(substr.toUpperCase())
                    {
                        case "GET" :
                            String[] tabString = str.split("\\s+");
                            resultat = disqueDur.get(tabString[1]);
                            break;
                        case "SET" :
                            String[] splitSpace = str.split("\\s+");

                            String regex = "\"([^\"]*)\"";
                            Pattern pattern = Pattern.compile(regex);
                            Matcher matcher = pattern.matcher(splitSpace[2]);

                            if (matcher.find()) {
                                String extractedString = matcher.group(1);
                                System.out.println(splitSpace[1]);
                                System.out.println(extractedString);
                                disqueDur.put(splitSpace[1], extractedString);
                                resultat = "\""+extractedString+"\"";
                            } else {
                                resultat = "Erreur extraite.";
                            }
                            break;
                        case "STR" : // Nabil
                            if (Objects.equals(str.substring(0, 6).toUpperCase(), "STRLEN")){
                                System.out.println("STRLEN");
                            }
                            break;
                        case "APP" : // Nabil
                            //TODO prendre en compte qu'il y a 2 paramètres
                            if (Objects.equals(str.substring(0, 6).toUpperCase(), "APPEND")){
                                System.out.println("APPEND");
                            }
                            break;
                        case "INC" : // Hugo
                            if (str.substring(0,4).toUpperCase() == "INCR"){

                            }
                            break;
                        case "DEC" : // Hugo
                            if (str.substring(0,4).toUpperCase() == "DECR"){

                            }
                            break;
                        case "DEL" :
                            /*
                                on peut avoir plusieurs arguments (key)
                             */
                            String[] stringSplited = str.split("\\s+");
                            int nb = 0;
                            for (int i = 1; i < stringSplited.length; i++) {
                                if(disqueDur.del(str))
                                {
                                    nb++;
                                };
                            }
                            resultat = String.valueOf(nb);
                            break;
                        case "EXI" :
                            if (Objects.equals(str.substring(0, 6).toUpperCase(), "EXISTS")){
                                /*
                                    on peut avoir plusieurs arguments (key)
                                 */
                                String[] strSplited = str.split("\\s+");
                                int nbExist = 0;

                                for (int i = 1; i < strSplited.length; i++) {
                                    String currentKey = strSplited[i];
                                    if(disqueDur.exist(currentKey))
                                    {
                                        nbExist++;
                                    }
                                }
                                resultat = String.valueOf((nbExist));
                            }
                            break;
                        case "EXP" ://Adrien
                            //TODO prendre en compte qu'il y a 2 paramètres
                            if (Objects.equals(str.substring(0, 6).toUpperCase(), "EXPIRE")){

                            }
                            break;
                        case "SUB" : //Adrien
                            if (Objects.equals(str.substring(0, 9).toUpperCase(), "SUBSCRIBE")){

                            }
                            break;
                        case "PUB" : //
                            if (Objects.equals(str.substring(0, 7).toUpperCase(), "PUBLISH")){

                            }
                            break;
                        case "UNS" : //
                            if (Objects.equals(str.substring(0, 11).toUpperCase(), "UNSUBSCRIBE")){

                            }
                            break;

                        case "QUI" : //
                            if (Objects.equals(str.substring(0, 4).toUpperCase(), "QUIT")){
                                start = false;
                                resultat = "Serveur est fermé.";
                            }
                            break;
                        default:
                            resultat = "Commande non disponible";
                            break;
                    }
                    out.write(("HTTP/1.0 200 OK\n"+resultat).getBytes());
                }
            }

        } catch(IOException e) {
            e.printStackTrace(System.err);
        }
    }

}