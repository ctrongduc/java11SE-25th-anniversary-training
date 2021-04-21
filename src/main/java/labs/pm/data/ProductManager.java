package labs.pm.data;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ProductManager {
    private final Logger logger = Logger.getLogger(ProductManager.class.getName());

    private Map<Product, List<Review>> products = new HashMap<>();
    private ResourceFormatter formatter;

    private static Map<String, ResourceFormatter> formatters
            = Map.of("en-GB", new ResourceFormatter(Locale.UK),
            "en-US", new ResourceFormatter(Locale.US),
            "fr-FR", new ResourceFormatter(Locale.FRANCE),
            "zh-CN", new ResourceFormatter(Locale.CHINA),
            "ru-RU", new ResourceFormatter(new Locale("ru", "RU")));

    public ProductManager(Locale locale) {
        this(locale.toLanguageTag());
    }

    public ProductManager(String languageTag) {
        changeLocale(languageTag);
    }

    public void changeLocale(String languageTag) {
        formatter = formatters.getOrDefault(languageTag, formatters.get("en-US"));
    }

    public static Set<String> getSupportedLocales() {
        return formatters.keySet();
    }

    public Product createProduct(int id, String name, BigDecimal price, Rating rating, LocalDate bestBefore) {
        Product product = new Food(id, name, price, rating, bestBefore);
        products.putIfAbsent(product, new ArrayList<>());
        return product;
    }

    public Product createProduct(int id, String name, BigDecimal price, Rating rating) {
        Product product = new Drink(id, name, price, rating);
        products.putIfAbsent(product, new ArrayList<>());
        return product;
    }

    public Product reviewProduct(int id, Rating rating, String comment) {
        return this.reviewProduct(findProduct(id), rating, comment);

    }

    public Product reviewProduct(Product product, Rating rating, String comment) {
        List<Review> reviews = products.get(product);
        products.remove(product, reviews);
        reviews.add(new Review(rating, comment));

        int avg = (int) Math.round(reviews.stream()
                .mapToInt(r -> r.getRating().ordinal())
                .average()
                .orElse(0));

        products.put(product.applyRating(avg), reviews);
        return product;
    }

    public Product findProduct(int id) {
        return products.keySet()
                    .stream()
                    .filter(p -> p.getId() == id)
                    .findFirst()
                    .orElseThrow(() -> new ProductManagerException("Product with id "+ id + "not found"));
                    //.orElseGet(null);
    }

    public void printProductReport(int id) {
        this.printProductReport(findProduct(id));
    }

    public void printProductReport(Product product) {
        StringBuilder txt = new StringBuilder();
        List<Review> reviews = products.get(product);
        Collections.sort(reviews);
        txt.append(formatter.formatProduct(product));
        txt.append("\n");
//        for (Review review : reviews) {
//            txt.append(formatter.formatReview(review));
//            txt.append("\n");
//        }
        if (reviews.isEmpty()) {
            txt.append(formatter.getText("no.reviews")).append('\n');
        } else {
            // 1st option, this might have problem with parallel mode
            reviews.stream().forEach(r -> txt.append(formatter.formatReview(r)).append('\n'));
            // 2nd option, using map, this works fine with parallel,
            // txt.append(reviews.stream().map(r -> formatter.formatReview(r) + '\n').collect(Collectors.joining()));
        }
        System.out.println(txt.toString());
    }

    public void printProducts(Predicate<Product> filter, Comparator<Product> sorter) {
        StringBuilder txt = new StringBuilder();
        products.keySet()
                .stream()
                .sorted(sorter)
                .filter(filter)
                .forEach(p -> txt.append(formatter.formatProduct(p)).append('\n'));
        System.out.println(txt);
    }

    public void printProducts(Comparator<Product> sorter) {
        StringBuilder txt = new StringBuilder();
        products.keySet()
                .stream()
                .sorted(sorter)
                .forEach(p -> txt.append(formatter.formatProduct(p)).append('\n'));
        System.out.println(txt);
    }

    public Map<String, String> getDiscount() {
        return products.keySet()
                .stream()
                .collect(
                        Collectors.groupingBy(
                                product -> product.getRating().getStars(),
                                Collectors.collectingAndThen(
                                        Collectors.summingDouble(product -> product.getDiscount().doubleValue()),
                                        discount -> formatter.moneyFormat.format(discount)
                                )
                        )
                );

    }

    public static class ResourceFormatter {
        private Locale locale;
        private ResourceBundle resources;
        private DateTimeFormatter dateFormat;
        private NumberFormat moneyFormat;

        public ResourceFormatter(Locale locale) {
            this.locale = locale;
            resources = ResourceBundle.getBundle("resources", locale);
            dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).localizedBy(locale);
            moneyFormat = NumberFormat.getCurrencyInstance(locale);
        }

        private String formatProduct(Product product) {
            return MessageFormat.format(resources.getString("product"),
                        product.getName(),
                        moneyFormat.format(product.getPrice()),
                        product.getRating().getStars(),
                        dateFormat.format(product.getBestBefore()));
        }

        private String formatReview(Review review) {
            return MessageFormat.format(resources.getString("review"),
                        review.getRating().getStars(),
                        review.getComment());
        }

        private String getText(String key) {
            return resources.getString(key);
        }
    }
}
