// Test JavaScript file for syntax highlighting
function calculateTotal(items) {
    let total = 0;
    const taxRate = 0.08;
    
    for (const item of items) {
        if (item.price > 0) {
            total += item.price * item.quantity;
        }
    }
    
    return total * (1 + taxRate);
}

// ES6 features
const arrowFunction = (x, y) => x + y;
const templateString = `Total: ${calculateTotal([{price: 10, quantity: 2}])}`;

// Async/await
async function fetchData() {
    try {
        const response = await fetch('/api/data');
        const data = await response.json();
        return data;
    } catch (error) {
        console.error('Error:', error);
    }
}

// Class definition
class ShoppingCart {
    constructor() {
        this.items = [];
    }
    
    addItem(item) {
        this.items.push(item);
    }
    
    getTotal() {
        return calculateTotal(this.items);
    }
}

export { calculateTotal, ShoppingCart };