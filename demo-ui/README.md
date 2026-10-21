# SHOPFLOW API Demo UI

This is a zero-build static frontend for demonstrating the iteration-2 API contract.

## Run it

1. Start the backend services as described in the repository root README. For the complete order → payment → inventory event flow, start Kafka and use the `kafka` profile for Order, Inventory and Payment.
2. Serve this directory over HTTP (do not open `index.html` directly). Run **one** of the following commands and keep that terminal open:

   From the repository root (`D:\projects\318e-commerce-main`):

   ```powershell
   python -m http.server 4174 --bind 127.0.0.1 --directory demo-ui
   ```

   Or, if the terminal is already in `D:\projects\318e-commerce-main\demo-ui`:

   ```powershell
   python -m http.server 4174 --bind 127.0.0.1
   ```

3. Open `http://127.0.0.1:4174`.

Use the seeded administrator account from the root README (`admin@318ecommerce.local` / `ChangeMe123!`) to access protected screens. The storefront remains available without login.

## API coverage

- Product browsing: `GET /products`
- Login: `POST /users/login`
- Order list, create and cancel: `/orders`
- Mock payment: `POST /payments`
- Inventory read and admin adjustment: `/inventory`
- Admin analytics and low-stock alerts
- AI Customer Service sessions and messages

The UI contains no product, order, stock, or analytics mock data. If a service is unavailable, the associated panel displays the API error so it is clear during a demonstration.
