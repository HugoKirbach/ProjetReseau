package fr.ul.miage.reseau.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Lit l'entrée standard et envoie la chaîne saisie
 * au serveur pour conversion en majuscule.
 * Ne gère que des chaînes d'au plus 80 **octets**.
 */
public class Client implements Runnable {

    public static void main(String[] args) {
        (new Client()).run();
    }

    @Override
    public void run() {
        try {
            final InetAddress remoteAddress = InetAddress.getByName("127.0.0.1");
            try(final Socket server = new Socket(remoteAddress, 6379)) {
                final OutputStream out = server.getOutputStream();
                final InputStream in = server.getInputStream();

                final BufferedReader br = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));

                final byte[] buffer = new byte[80];
                while (true) {
                    String line = br.readLine();
                    if (line != null) {
                        // envoi de la chaîne au serveur
                        out.write(line.getBytes(StandardCharsets.ISO_8859_1));

                        // récupération de la chaîne en majuscules
                        final int numRead = in.read(buffer);

                        // le serveur utilise de l'utf-8 donc si on envoie des caractères exotiques, crack !
                        System.out.println(new String(buffer, 0, numRead, StandardCharsets.ISO_8859_1));
                    }
                }
            }

        } catch(IOException e) {
            e.printStackTrace(System.err);
        }
    }

}