package fr.ul.miage.reseau.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
