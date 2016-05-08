This is a prototype of an _Inflatatble Transpose Tree_ as described in this blog post:

[The Inflatable Transpose Tree: a highly compact data structure](https://engineering.vena.io/2016/05/09/transpose-tree/)

To use a Transpose Tree, you extend `TransposeTree` and add additional field-arrays to hold whatever would have been in fields, had you used ordinary key and value objects.  The subclass must implement a `compare` method that allows `TransposeTree` to set up the tree references without ever manipulating the keys directly.  This way, a key can be any primitive, or any combination of primitives.  In other words, compound keys are straightforward.  (And hey&mdash;the keys can even be objects, if you want.)

For more information, see the blog post.
