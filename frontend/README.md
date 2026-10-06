# bank-web

Angular-frontend van BankSim. Zie de [README in de root](../README.md) en het [technisch ontwerp](../doc/technisch-ontwerp.md), hoofdstuk 2 en 10.4.

```bash
npm ci
npm start        # ontwikkelserver op http://localhost:4200
npm test         # Vitest unit-tests
npm run lint     # ESLint (angular-eslint)
npm run build    # productie-build naar dist/bank-web
```

Install-scripts van dependencies staan standaard uit (`allowScripts` in `package.json`); de build heeft ze niet nodig.
