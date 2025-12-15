## Neon Connection String Fix

Your connection string has `channel_binding=require` which causes the "No suitable driver" error.

**Current (broken):**
```
postgresql://neondb_owner:npg_lW24HuwoysAZ@ep-nameless-term-a4r6675u-pooler.us-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
```

**Fixed version:**
```
postgresql://neondb_owner:npg_lW24HuwoysAZ@ep-nameless-term-a4r6675u-pooler.us-east-1.aws.neon.tech/neondb?sslmode=require
```

**What to do:**

1. Open `config.properties`
2. Change the `db.url` line to:
   ```properties
   db.url=postgresql://neondb_owner:npg_lW24HuwoysAZ@ep-nameless-term-a4r6675u-pooler.us-east-1.aws.neon.tech/neondb?sslmode=require
   ```
3. Save the file
4. Run the scraper again

**Just remove `&channel_binding=require` from the end!**
