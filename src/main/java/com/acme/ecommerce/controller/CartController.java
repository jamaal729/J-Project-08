package com.acme.ecommerce.controller;

import com.acme.ecommerce.FlashMessage;
import com.acme.ecommerce.domain.OrderQuantityGreaterThanStockException;
import com.acme.ecommerce.domain.Product;
import com.acme.ecommerce.domain.ProductPurchase;
import com.acme.ecommerce.domain.Purchase;
import com.acme.ecommerce.domain.ShoppingCart;
import com.acme.ecommerce.service.ProductService;
import com.acme.ecommerce.service.PurchaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;

import static com.acme.ecommerce.FlashMessage.Status.FAILURE;
import static com.acme.ecommerce.FlashMessage.Status.SUCCESS;

@Controller
@RequestMapping("/cart")
@Scope("request")
public class CartController {
    final Logger logger = LoggerFactory.getLogger(CartController.class);

    @Autowired
    PurchaseService purchaseService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ShoppingCart sCart;

    @Autowired
    private HttpSession session;

    @RequestMapping("")
    public String viewCart(Model model) {
        logger.debug("Getting Product List");
        logger.debug("Session ID = " + session.getId());

        // model.addAttribute("page_title", "View Cart");
        Purchase purchase = sCart.getPurchase();
        BigDecimal subTotal = new BigDecimal(0);

        model.addAttribute("purchase", purchase);
        if (purchase != null) {
            for (ProductPurchase pp : purchase.getProductPurchases()) {
                logger.debug("cart has " + pp.getQuantity() + " of " + pp.getProduct().getName());
                subTotal = subTotal.add(pp.getProduct().getPrice().multiply(new BigDecimal(pp.getQuantity())));
            }
            if (!subTotal.equals(new BigDecimal(0))) {
                model.addAttribute("subTotal", subTotal);
            }
        } else {
            logger.error("No purchases Found for session ID=" + session.getId());
            return "redirect:/error";
        }
        CartController.addCart(model, sCart);

        return "cart";
    }

    @RequestMapping(path = "/add", method = RequestMethod.POST)
    public RedirectView addToCart(
            @ModelAttribute(value = "productId") long productId,
            @ModelAttribute(value = "quantity") int quantity,
            RedirectAttributes attributes) {
        boolean productAlreadyInCart = false;
        RedirectView redirect = new RedirectView("/product/");
        redirect.setExposeModelAttributes(false);

        Product addProduct = productService.findById(productId);

        // TODO Check for product availability before adding (done)
        productService.checkProductStock(addProduct, quantity);

        if (addProduct != null) {
            logger.debug("Adding Product: " + addProduct.getId());

            Purchase purchase = sCart.getPurchase();
            if (purchase == null) {
                purchase = new Purchase();
                sCart.setPurchase(purchase);
            } else {
                for (ProductPurchase pp : purchase.getProductPurchases()) {
                    if (pp.getProduct() != null) {
                        if (pp.getProduct().getId().equals(productId)) {
                            pp.setQuantity(pp.getQuantity() + quantity);
                            productAlreadyInCart = true;
                            break;
                        }
                    }
                }
            }
            if (!productAlreadyInCart) {
                ProductPurchase newProductPurchase = new ProductPurchase();
                newProductPurchase.setProduct(addProduct);
                newProductPurchase.setQuantity(quantity);
                newProductPurchase.setPurchase(purchase);
                purchase.getProductPurchases().add(newProductPurchase);
            }
            String message = "Added " + quantity + " of " + addProduct.getName() + " to cart";
            logger.debug(message);
            // TODO: Enhancement 6
            // Added flash message for adding products to the cart
            attributes.addFlashAttribute("flash", new FlashMessage(message, SUCCESS));
            sCart.setPurchase(purchaseService.save(purchase));
        } else {
            logger.error("Attempt to add unknown product: " + productId);
            redirect.setUrl("/error");
        }

        return redirect;
    }

    @RequestMapping(path = "/update", method = RequestMethod.POST)
    public RedirectView updateCart(@ModelAttribute(value = "productId") long productId,
                                   @ModelAttribute(value = "newQuantity") int newQuantity,
                                   RedirectAttributes attributes) {
        logger.debug("Updating Product: " + productId + " with Quantity: " + newQuantity);
        RedirectView redirect = new RedirectView("/cart");
        redirect.setExposeModelAttributes(false);

        Product updateProduct = productService.findById(productId);

        // TODO - check stock quantity
        // Check for product availability before updating
        productService.checkProductStock(updateProduct, newQuantity);

        if (updateProduct != null) {
            Purchase purchase = sCart.getPurchase();
            if (purchase == null) {
                logger.error("Unable to find shopping cart for update");
                redirect.setUrl("/error");
            } else {
                for (ProductPurchase pp : purchase.getProductPurchases()) {
                    if (pp.getProduct() != null) {
                        if (pp.getProduct().getId().equals(productId)) {
                            if (newQuantity > 0) {
                                pp.setQuantity(newQuantity);
                                String message = "Updated " + updateProduct.getName() + " to " + newQuantity;
                                logger.debug(message);
                                // TODO: Enhancement 6
                                // Added flash message for updating the cart - success
                                attributes.addFlashAttribute("flash", new FlashMessage(message, SUCCESS));
                            } else {
                                purchase.getProductPurchases().remove(pp);
                                String message = "Removed " + updateProduct.getName() + " because quantity was set to " + newQuantity;
                                logger.debug(message);
                                // TODO: Enhancement 6
                                // Added flash message for updating the cart - failure
                                attributes.addFlashAttribute("flash", new FlashMessage(message, FAILURE));
                            }
                            break;
                        }
                    }
                }
            }
            sCart.setPurchase(purchaseService.save(purchase));
        } else {
            logger.error("Attempt to update on non-existent product");
            redirect.setUrl("/error");
        }

        return redirect;
    }

    @RequestMapping(path = "/remove", method = RequestMethod.POST)
    public RedirectView removeFromCart(@ModelAttribute(value = "productId") long productId,
                                       RedirectAttributes attributes) {
        logger.debug("Removing Product: " + productId);
        RedirectView redirect = new RedirectView("/cart");
        redirect.setExposeModelAttributes(false);

        Product updateProduct = productService.findById(productId);
        if (updateProduct != null) {
            Purchase purchase = sCart.getPurchase();
            if (purchase != null) {
                for (ProductPurchase pp : purchase.getProductPurchases()) {
                    if (pp.getProduct() != null) {
                        if (pp.getProduct().getId().equals(productId)) {
                            purchase.getProductPurchases().remove(pp);
                            String message = "Removed " + updateProduct.getName() + " from cart";
                            logger.debug(message);
                            // TODO: Enhancement 6
                            // Added flash message for removing products from the cart
                            attributes.addFlashAttribute("flash", new FlashMessage(message, SUCCESS));
                            break;
                        }
                    }
                }
                purchase = purchaseService.save(purchase);
                sCart.setPurchase(purchase);
                if (purchase.getProductPurchases().isEmpty()) {
                    redirect.setUrl("/product/");
                }
            } else {
                logger.error("Unable to find shopping cart for update");
                redirect.setUrl("/error");
            }
        } else {
            logger.error("Attempt to update on non-existent product");
            redirect.setUrl("/error");
        }

        return redirect;
    }

    @RequestMapping(path = "/empty", method = RequestMethod.POST)
    public RedirectView emptyCart(RedirectAttributes redirectAttributes) {
        RedirectView redirect = new RedirectView("/product/");
        redirect.setExposeModelAttributes(false);

        logger.debug("Emptying Cart");
        Purchase purchase = sCart.getPurchase();
        if (purchase != null) {
            purchase.getProductPurchases().clear();
            sCart.setPurchase(purchaseService.save(purchase));
            redirectAttributes.addFlashAttribute("flash", new FlashMessage("Cart is emptied.", FlashMessage.Status.SUCCESS));
        } else {
            logger.error("Unable to find shopping cart for update");
            redirect.setUrl("/error");
        }

        return redirect;
    }

    // TODO: Bugfix2, Enhancement6:
    @ExceptionHandler(OrderQuantityGreaterThanStockException.class)
    public String exceedsStock(HttpServletRequest request, Exception ex) {
        String message = ex.getMessage();
        logger.error(message);
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        flashMap.put("flash", new FlashMessage(message, FAILURE));
        return "redirect:" + request.getHeader("referer");
    }

    public static void addCart(Model model, ShoppingCart sCart) {
        BigDecimal subTotal = new BigDecimal(0);
        model.addAttribute("cart", sCart);
        for (ProductPurchase pp : sCart.getPurchase().getProductPurchases()) {
            subTotal = subTotal.add(pp.getProduct().getPrice().multiply(new BigDecimal(pp.getQuantity())));
        }
        if (!subTotal.equals(new BigDecimal(0))) {
            model.addAttribute("subTotal", subTotal);
        }
    }
}
