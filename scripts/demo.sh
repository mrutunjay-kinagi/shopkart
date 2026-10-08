#!/usr/bin/env bash
# End-to-end walkthrough of the ShopKart API against a running instance (docker compose up -d --build).
# Requires curl, jq and openssl.  Usage: scripts/demo.sh [base-url]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
API="$BASE/api/v1"
WEBHOOK_SECRET="${MOCKPAY_WEBHOOK_SECRET:-mockpay-local-webhook-secret}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@shopkart.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@12345}"
EMAIL="demo.$(date +%s)@example.com"

step() { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }
json() { curl -sS -H 'Content-Type: application/json' "$@"; }

step "Health"
curl -sS "$BASE/actuator/health" | jq -c .

step "Browse: categories and keyword search 'wireless headphones'"
curl -sS "$API/categories" | jq -c '[.[] | {id, name, parentId}]'
curl -sS "$API/products?q=wireless%20headphones" | jq -c '{totalElements, first: .content[0] | {id, name, price, inStock}}'
PRODUCT_ID=$(curl -sS "$API/products?q=wireless%20headphones" | jq -r '.content[0].id')
ACCESSORY_ID=$(curl -sS "$API/products?q=jbl%20speaker" | jq -r '.content[0].id')

step "Product details (second call is served from Redis)"
curl -sS "$API/products/$PRODUCT_ID" | jq -c '{name, price, mrp, discountPercent, stockQuantity, specifications}'

step "Register $EMAIL"
TOKEN=$(json -X POST "$API/auth/register" -d "{\"email\":\"$EMAIL\",\"password\":\"Passw0rd!\",\"fullName\":\"Demo Customer\",\"phone\":\"9876543210\"}" | jq -r .accessToken)
AUTH=(-H "Authorization: Bearer $TOKEN")
curl -sS "${AUTH[@]}" "$API/users/me" | jq -c .

step "Cart: add items and review totals"
json "${AUTH[@]}" -X POST "$API/cart/items" -d "{\"productId\":$PRODUCT_ID,\"quantity\":1}" >/dev/null
json "${AUTH[@]}" -X POST "$API/cart/items" -d "{\"productId\":$ACCESSORY_ID,\"quantity\":2}" | jq -c '{items: [.items[] | {name, quantity, lineTotal}], subtotal, savings, shippingFee, total, checkoutReady}'

step "Checkout (card), idempotent"
ADDRESS='{"recipientName":"Demo Customer","phone":"9876543210","line1":"42 Residency Road","city":"Bengaluru","state":"Karnataka","postalCode":"560025"}'
ORDER=$(json "${AUTH[@]}" -H "Idempotency-Key: demo-$(date +%s)" -X POST "$API/orders/checkout" -d "{\"shippingAddress\":$ADDRESS,\"paymentMethod\":\"CARD\"}")
echo "$ORDER" | jq -c '{id, orderNumber, status, totalAmount, nextAction, paymentDueAt}'
ORDER_ID=$(echo "$ORDER" | jq -r .id)

step "Pay with a declined card, then a good card"
json "${AUTH[@]}" -X POST "$API/orders/$ORDER_ID/payments" -d '{"cardToken":"tok_fail_insufficient_funds"}' | jq -c '{status, failureReason}'
PAYMENT=$(json "${AUTH[@]}" -X POST "$API/orders/$ORDER_ID/payments" -d '{"cardToken":"tok_visa_4242"}')
echo "$PAYMENT" | jq -c '{id, status, instrument, receiptNumber}'
PAYMENT_ID=$(echo "$PAYMENT" | jq -r .id)

step "Order is confirmed asynchronously via Kafka (PaymentSucceeded -> order-service)"
for _ in $(seq 1 20); do
  STATUS=$(curl -sS "${AUTH[@]}" "$API/orders/$ORDER_ID" | jq -r .status)
  [ "$STATUS" = "CONFIRMED" ] && break
  sleep 0.5
done
echo "order status: $STATUS"
curl -sS "${AUTH[@]}" "$API/payments/$PAYMENT_ID/receipt" | jq -c '{receiptNumber, orderNumber, method, instrument, totalPaid, lines: [.lines[] | .description]}'

step "Admin ships and delivers the order"
ADMIN=$(json -X POST "$API/auth/login" -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r .accessToken)
json -H "Authorization: Bearer $ADMIN" -X PATCH "$API/admin/orders/$ORDER_ID/status" -d '{"status":"SHIPPED","carrier":"BlueDart","trackingNumber":"BD7781234560IN"}' | jq -c '{status}'
curl -sS "${AUTH[@]}" "$API/orders/$ORDER_ID/tracking" | jq -c '{status, carrier, trackingNumber, estimatedDelivery, timeline: [.timeline[] | .status]}'
json -H "Authorization: Bearer $ADMIN" -X PATCH "$API/admin/orders/$ORDER_ID/status" -d '{"status":"DELIVERED"}' | jq -c '{status}'

step "UPI order confirmed by a signed gateway webhook"
json "${AUTH[@]}" -X POST "$API/cart/items" -d "{\"productId\":$ACCESSORY_ID,\"quantity\":1}" >/dev/null
UPI_ORDER=$(json "${AUTH[@]}" -X POST "$API/orders/checkout" -d "{\"shippingAddress\":$ADDRESS,\"paymentMethod\":\"UPI\"}" | jq -r .id)
UPI_PAYMENT=$(json "${AUTH[@]}" -X POST "$API/orders/$UPI_ORDER/payments" -d '{"upiVpa":"demo.customer@okicici"}')
echo "$UPI_PAYMENT" | jq -c '{status, instrument}'
REF=$(docker compose exec -T mysql mysql -ushopkart -pshopkart -N -e "SELECT gateway_reference FROM shopkart.payments WHERE order_id=$UPI_ORDER ORDER BY id DESC LIMIT 1" 2>/dev/null)
BODY="{\"gatewayReference\":\"$REF\",\"status\":\"SUCCEEDED\"}"
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" -hex | awk '{print $NF}')
curl -sS -o /dev/null -w 'webhook -> HTTP %{http_code}\n' -H 'Content-Type: application/json' -H "X-MockPay-Signature: $SIG" -X POST "$API/payments/webhooks/mockpay" -d "$BODY"
sleep 2
curl -sS "${AUTH[@]}" "$API/orders/$UPI_ORDER" | jq -c '{orderNumber, status}'

step "Order history"
curl -sS "${AUTH[@]}" "$API/orders" | jq -c '[.content[] | {orderNumber, status, itemCount, totalAmount}]'

step "Logout revokes the token immediately"
curl -sS -o /dev/null -w 'logout -> HTTP %{http_code}\n' "${AUTH[@]}" -X POST "$API/auth/logout"
curl -sS -o /dev/null -w 'GET /users/me after logout -> HTTP %{http_code}\n' "${AUTH[@]}" "$API/users/me"

printf '\nE-mails sent during the demo: http://localhost:8025   API docs: %s/swagger-ui.html\n' "$BASE"
