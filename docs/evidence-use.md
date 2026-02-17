This is the summoner pattern (also called an "implicit materializer"). It's a convenience method to retrieve implicit instances.

### What It Does

```scala
def apply[F[_]](implicit ev: WikiSource[F]): WikiSource[F] = ev
//              ^^^^^^^^^^^^^^^^^^^^^^^^
//              "ev" is evidence that a WikiSource[F] exists in scope
```

It asks the compiler: "Give me the implicit WikiSource[F] that exists in scope" and returns it.

### How It's Used

Without the apply method:

```scala
// Verbose way to get an implicit instance
implicitly[WikiSource[IO]]
// or
summon[WikiSource[IO]]  // Scala 3
```

With the apply method:

```scala
WikiSource[IO]  // Cleaner! Calls WikiSource.apply[IO]
```

The compiler sees this and:

- Looks for an implicit WikiSource[IO] in scope
- Passes it as the ev parameter
- Returns it

### Example Usage

```scala
// First, you create an instance and make it implicit
implicit val wikiSource: WikiSource[IO] = WikiSource.impl[IO](client)

// Later, you can summon it
val source = WikiSource[IO]  // Uses apply method
source.streamEvents          // Call methods on it
```

### Why "Evidence"?

The parameter is called `ev` (evidence) because it proves to the compiler that a `WikiSource[F]` instance exists. If none exists, compilation fails:

```scala
WikiSource[IO]  // ❌ Compile error: "could not find implicit value"
```

TL;DR: It's syntactic sugar to retrieve implicit instances. `WikiSource[IO]` is cleaner than `implicitly[WikiSource[IO]]`. Common pattern in Cats/Cats Effect (like `Sync[F]`, `Async[F]`).
