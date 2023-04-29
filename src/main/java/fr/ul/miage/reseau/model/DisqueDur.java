package fr.ul.miage.reseau.model;

import java.util.*;
import java.util.stream.Collectors;

public class DisqueDur {

    private List<Fichier> fichiers  = new ArrayList<Fichier>();

    public List<String> dir() {
        return fichiers.stream().map(f -> f.getNom()).collect(Collectors.toList());
    }

    public String get(String nom) {
        return fichiers.stream().filter(f -> f.getNom().equals(nom)).map(f -> f.getContenu()).findAny().orElse(null);
    }

    public void put(String nom, String contenu) {
        final Optional<Fichier> existant = fichiers.stream().filter(f -> f.getNom().equals(nom)).findAny();
        if(existant.isPresent()) {
            existant.get().setContenu(contenu);
        } else {
            this.fichiers.add(new Fichier(nom, contenu));
        }
    }

    public boolean del(String nom) {
        final Optional<Fichier> existant = fichiers.stream().filter(f -> f.getNom().equals(nom)).findAny();

        if(existant.isPresent()) {
            this.fichiers.remove(existant.get());
        }

        return existant.isPresent();
    }

    public boolean exist(String nom) {
        final Optional<Fichier> existant = fichiers.stream().filter(f -> f.getNom().equals(nom)).findAny();
        return existant.isPresent();
    }

    //mettre cpt ici
    public void ExpireDuration(String nom, String second){
        System.out.println(second);
        final Optional<Fichier> existant = fichiers.stream().filter(f -> f.getNom().equals(nom)).findAny();
        int seconds = Integer.parseInt(second); // Convertir la chaîne en un entier
        Timer timer = new Timer();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {

                //supprimer ici
                delete(nom);
            }
        };
        timer.schedule(task, seconds * 1000);


    }

    public boolean containsKey(String nom) {
        return exist(nom);
    }

    public void delete(String nom){
        for (Fichier fichier : this.fichiers) {

            if (fichier.getNom().equals(nom)) {
                this.fichiers.remove(fichier);
                break;
            }
        }
    }
    public void display()
    {
        fichiers.stream().forEach(f -> System.out.println(f.getNom() + " - "+ f.getContenu()));
    }
    public static class Fichier {
        private final String nom;
        private String contenu;
        public Fichier(String nom, String contenu) {
            this.contenu = contenu;
            this.nom = nom;
        }
        public String getContenu() {
            return contenu;
        }
        public void setContenu(String contenu) {
            this.contenu = contenu;
        }
        public String getNom() {
            return nom;
        }
    }
}
